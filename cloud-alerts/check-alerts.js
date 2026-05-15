const admin = require("firebase-admin");

const ALERT_USERS = "alertUsers";
const NAIROBI = "Africa/Nairobi";
const NEW_YORK = "America/New_York";
const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "stockwatchdog-41541";
const DRY_RUN = process.argv.includes("--dry-run");
const FMP_API_KEY = process.env.FMP_API_KEY || "";
const FINNHUB_API_KEY = process.env.FINNHUB_API_KEY || "";
const TWELVE_DATA_API_KEY = process.env.TWELVE_DATA_API_KEY || "";
const ALPHA_VANTAGE_API_KEY = process.env.ALPHA_VANTAGE_API_KEY || "";
const SEND_TEST_NOTIFICATION = process.env.SEND_TEST_NOTIFICATION === "true";

main().catch((error) => {
  console.error("Cloud alert run failed", error);
  process.exitCode = 1;
});

async function main() {
  initializeFirebase();
  const db = admin.firestore();
  const users = await db.collection(ALERT_USERS).where("active", "==", true).get();
  console.log(`Checking ${users.size} active cloud alert user(s)`);

  if (SEND_TEST_NOTIFICATION) {
    let sent = 0;
    for (const userDoc of users.docs) {
      const token = String((userDoc.data() || {}).fcmToken || "");
      if (!token) continue;
      await sendAlertMessage(token, {
        symbol: "SETUP",
        title: "Stock Watchdog ready",
        body: "Cloud alerts are connected and can notify this device while the app is closed.",
        route: "alerts",
        type: "SETUP_TEST",
      });
      sent += 1;
    }
    console.log(`Sent ${sent} setup test notification(s)`);
    return;
  }

  for (const userDoc of users.docs) {
    try {
      await checkUser(db, userDoc);
    } catch (error) {
      console.error("User alert check failed", {
        uid: userDoc.id,
        message: error && error.message,
      });
    }
  }
}

function initializeFirebase() {
  if (admin.apps.length > 0) return;

  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) {
    throw new Error("Missing FIREBASE_SERVICE_ACCOUNT_JSON GitHub secret.");
  }

  const serviceAccount = JSON.parse(raw.replace(/^\uFEFF/, "").trim());
  if (serviceAccount.private_key) {
    serviceAccount.private_key = serviceAccount.private_key.replace(/\\n/g, "\n");
  }

  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    projectId: PROJECT_ID,
  });
}

async function checkUser(db, userDoc) {
  const user = userDoc.data();
  const token = String(user.fcmToken || "");
  if (!token || !user.notificationsEnabled || !user.firebasePushEnabled) return;

  const now = new Date();
  const alerts = Array.isArray(user.alerts) ? user.alerts.filter((a) => a && a.enabled) : [];
  const trackedSymbols = new Set(
    (Array.isArray(user.trackedSymbols) ? user.trackedSymbols : [])
      .map(normalizeSymbol)
      .filter(Boolean)
  );
  alerts.forEach((alert) => {
    const symbol = normalizeSymbol(alert.symbol);
    if (symbol) trackedSymbols.add(symbol);
  });

  if (trackedSymbols.size === 0) return;

  const quotes = new Map();
  const details = new Map();
  const detailSymbols = new Set(
    alerts
      .filter((alert) => needsDetails(alert.type))
      .map((alert) => normalizeSymbol(alert.symbol))
      .filter(Boolean)
  );
  trackedSymbols.forEach((symbol) => detailSymbols.add(symbol));

  for (const symbol of trackedSymbols) {
    quotes.set(symbol, await fetchQuote(symbol));
    await sleep(180);
  }
  for (const symbol of detailSymbols) {
    details.set(symbol, await fetchDetails(symbol));
    await sleep(180);
  }

  const settings = {
    marketHoursOnly: Boolean(user.marketHoursOnly),
    quietHoursEnabled: Boolean(user.quietHoursEnabled),
    quietHoursStartMinutes: Number(user.quietHoursStartMinutes || 0),
    quietHoursEndMinutes: Number(user.quietHoursEndMinutes || 0),
    platformFeePercent: Number(user.platformFeePercent || 0),
  };
  const isQuiet = isQuietHours(now, settings);
  const isMarketOpen = isUsMarketOpen(now);

  for (const alert of alerts) {
    const symbol = normalizeSymbol(alert.symbol);
    const quote = quotes.get(symbol);
    if (!symbol || !quote) continue;

    const result = await evaluateAlert({
      userRef: userDoc.ref,
      alert,
      quote,
      details: details.get(symbol),
      settings,
      now,
    });

    if (!result || !result.shouldNotify) continue;

    const marketOnly = alert.marketHoursOnly == null
      ? settings.marketHoursOnly
      : Boolean(alert.marketHoursOnly);
    const suppress = isQuiet || (marketOnly && !isMarketOpen);

    await logEvent(userDoc.ref, alert, result, quote, now, suppress);
    if (!suppress) {
      await sendAlertMessage(token, {
        symbol,
        title: `Stock Alert: ${symbol}`,
        body: result.message,
        route: `ticker/${symbol}`,
        type: String(alert.type || ""),
      });
    }
  }

  await checkAutomaticEarnings({
    userRef: userDoc.ref,
    token,
    trackedSymbols: Array.from(trackedSymbols),
    details,
    now,
    suppress: isQuiet,
  });
}

async function evaluateAlert({ userRef, alert, quote, details, settings, now }) {
  const id = String(alert.id || `${alert.symbol}-${alert.type}-${alert.threshold}`);
  const runtimeRef = userRef.collection("runtime").doc(`alert-${id}`);
  const runtimeSnap = await runtimeRef.get();
  const runtime = runtimeSnap.exists ? runtimeSnap.data() : {};
  if (runtime.serverDisabled) return null;

  const snoozedUntil = Number(alert.snoozedUntilMillis || 0);
  if (snoozedUntil > now.getTime()) return null;

  const type = String(alert.type || "");
  const threshold = Number(alert.threshold || 0);
  const prevCrossing = runtime.lastCrossingState ?? alert.lastCrossingState ?? false;
  const today = isoDateInZone(now, NAIROBI);
  const baseUpdate = {
    symbol: quote.symbol,
    type,
    lastPrice: quote.price,
    checkedAtMillis: now.getTime(),
  };

  let shouldNotify = false;
  let crossing = Boolean(prevCrossing);
  let message = null;
  let percentDate = runtime.lastPercentTriggerDate || alert.lastPercentTriggerDate || null;

  switch (type) {
    case "PRICE_ABOVE":
      crossing = quote.price > threshold;
      shouldNotify = crossing && !prevCrossing;
      message = shouldNotify ? `${quote.symbol} crossed above ${fmt(threshold)}` : null;
      break;
    case "PRICE_BELOW":
      crossing = quote.price < threshold;
      shouldNotify = crossing && !prevCrossing;
      message = shouldNotify ? `${quote.symbol} dropped below ${fmt(threshold)}` : null;
      break;
    case "PERCENT_CHANGE_DAY": {
      const pct = quote.percentChange;
      const triggered = pct != null && Math.abs(pct) >= Math.abs(threshold);
      shouldNotify = triggered && percentDate !== today;
      if (shouldNotify) percentDate = today;
      message = shouldNotify
        ? `${quote.symbol} moved ${pct >= 0 ? "up" : "down"} ${fmt(pct)}% today (rule: ${fmt(threshold)}%)`
        : null;
      break;
    }
    case "PERCENT_ABOVE_ENTRY":
    case "PERCENT_BELOW_ENTRY": {
      const entry = Number(alert.entryPrice || 0);
      if (entry <= 0) break;
      const netPct = netPercentVsEntry(quote.price, entry, settings.platformFeePercent);
      crossing = type === "PERCENT_ABOVE_ENTRY"
        ? netPct >= threshold
        : netPct <= -threshold;
      shouldNotify = crossing && !prevCrossing;
      message = shouldNotify
        ? `${quote.symbol} is ${fmt(netPct)}% net vs your entry (trigger: ${type === "PERCENT_ABOVE_ENTRY" ? "+" : "-"}${fmt(threshold)}%)`
        : null;
      break;
    }
    case "EARNINGS_REMINDER": {
      const release = details && details.nextEarningsEpochSeconds;
      if (!release) break;
      const daysAway = (release * 1000 - now.getTime()) / 86400000;
      const wanted = threshold > 0 ? threshold : 3;
      const triggered = daysAway >= 0 && daysAway <= wanted;
      shouldNotify = triggered && percentDate !== today;
      if (shouldNotify) percentDate = today;
      message = shouldNotify
        ? `${quote.symbol} reports earnings ${daysAway < 1 ? "today" : `in ${Math.floor(daysAway)} day(s)`}`
        : null;
      break;
    }
    case "FIFTY_TWO_WEEK_HIGH":
      if (!details || !details.fiftyTwoWeekHigh) break;
      crossing = quote.price >= details.fiftyTwoWeekHigh;
      shouldNotify = crossing && !prevCrossing;
      message = shouldNotify ? `${quote.symbol} touched a 52-week high (${fmt(quote.price)})` : null;
      break;
    case "FIFTY_TWO_WEEK_LOW":
      if (!details || !details.fiftyTwoWeekLow) break;
      crossing = quote.price <= details.fiftyTwoWeekLow;
      shouldNotify = crossing && !prevCrossing;
      message = shouldNotify ? `${quote.symbol} touched a 52-week low (${fmt(quote.price)})` : null;
      break;
    case "MA200_CROSS_UP":
      if (!details || !details.twoHundredDayAverage) break;
      crossing = quote.price >= details.twoHundredDayAverage;
      shouldNotify = crossing && !prevCrossing;
      message = shouldNotify ? `${quote.symbol} crossed above its 200-day MA (${fmt(details.twoHundredDayAverage)})` : null;
      break;
    case "MA200_CROSS_DOWN":
      if (!details || !details.twoHundredDayAverage) break;
      crossing = quote.price <= details.twoHundredDayAverage;
      shouldNotify = crossing && !prevCrossing;
      message = shouldNotify ? `${quote.symbol} crossed below its 200-day MA (${fmt(details.twoHundredDayAverage)})` : null;
      break;
    case "VOLUME_SPIKE": {
      const ratio = details && details.volumeSpikeRatio;
      const wanted = threshold > 0 ? threshold : 2;
      const triggered = ratio != null && ratio >= wanted;
      shouldNotify = triggered && percentDate !== today;
      if (shouldNotify) percentDate = today;
      message = shouldNotify ? `${quote.symbol} unusual volume: ${fmt(ratio)}x average` : null;
      break;
    }
    case "ANALYST_TARGET_REACH":
      if (!details || !details.analystTargetMean) break;
      crossing = quote.price >= details.analystTargetMean;
      shouldNotify = crossing && !prevCrossing;
      message = shouldNotify ? `${quote.symbol} reached analyst target (${fmt(details.analystTargetMean)})` : null;
      break;
    default:
      break;
  }

  const update = {
    ...baseUpdate,
    lastCrossingState: crossing,
    lastPercentTriggerDate: percentDate,
  };
  if (shouldNotify) {
    update.lastTriggeredAtMillis = now.getTime();
    if (alert.autoDisableAfterFire) update.serverDisabled = true;
  }

  if (!DRY_RUN) {
    await runtimeRef.set(update, { merge: true });
  }

  return { shouldNotify, message };
}

async function checkAutomaticEarnings({ userRef, token, trackedSymbols, details, now, suppress }) {
  const today = isoDateInZone(now, NAIROBI);
  for (const symbol of trackedSymbols) {
    const detail = details.get(symbol);
    const release = detail && detail.nextEarningsEpochSeconds;
    if (!release) continue;

    const releaseDate = isoDateInZone(new Date(release * 1000), NAIROBI);
    const tomorrow = isoDateInZone(new Date(now.getTime() + 86400000), NAIROBI);
    const releaseReached = today === releaseDate && now.getTime() >= release * 1000;
    const dayBefore = tomorrow === releaseDate;

    const key = dayBefore ? "day-before" : releaseReached ? "release" : null;
    if (!key) continue;

    const runtimeRef = userRef.collection("runtime").doc(`auto-earnings-${symbol}-${key}-${today}`);
    const seen = await runtimeRef.get();
    if (seen.exists) continue;

    const message = dayBefore
      ? `${symbol} results tomorrow`
      : `${symbol} results release time`;
    if (!DRY_RUN) {
      await runtimeRef.set({
        symbol,
        type: "EARNINGS_REMINDER",
        key,
        firedAtMillis: now.getTime(),
      });
      await logEvent(
        userRef,
        { id: null, symbol, type: "EARNINGS_REMINDER", threshold: key === "day-before" ? 1 : 0 },
        { message },
        null,
        now,
        suppress
      );
    }
    if (!suppress) {
      await sendAlertMessage(token, {
        symbol,
        title: `Results Alert: ${symbol}`,
        body: message,
        route: `ticker/${symbol}`,
        type: "EARNINGS_REMINDER",
      });
    }
  }
}

async function sendAlertMessage(token, payload) {
  if (!token) return;
  const body = String(payload.body || "Alert triggered");
  if (DRY_RUN) {
    console.log("Dry run notification", { symbol: payload.symbol, title: payload.title, body });
    return;
  }

  await admin.messaging().send({
    token,
    data: {
      symbol: String(payload.symbol || ""),
      title: String(payload.title || "Stock Watchdog"),
      body,
      message: body,
      route: String(payload.route || "alerts"),
      type: String(payload.type || ""),
    },
    android: {
      priority: "high",
    },
  });
}

async function logEvent(userRef, alert, result, quote, now, suppressed) {
  if (DRY_RUN) return;
  await userRef.collection("events").add({
    alertId: alert.id || null,
    symbol: normalizeSymbol(alert.symbol),
    type: String(alert.type || ""),
    message: result.message || "Alert triggered",
    priceAtTrigger: quote ? quote.price : null,
    threshold: alert.threshold ?? null,
    suppressed: Boolean(suppressed),
    firedAtMillis: now.getTime(),
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

async function fetchQuote(symbol) {
  const providers = [
    ["finnhub", () => fetchFinnhubQuote(symbol)],
    ["fmp", () => fetchFmpQuote(symbol)],
    ["twelveData", () => fetchTwelveDataQuote(symbol)],
    ["alphaVantage", () => fetchAlphaVantageQuote(symbol)],
    ["yahoo", () => fetchYahooQuote(symbol)],
  ];

  for (const [provider, fetcher] of providers) {
    try {
      const quote = await fetcher();
      if (isValidQuote(quote)) return quote;
    } catch (error) {
      console.warn("Quote fetch failed", { provider, symbol, message: error.message });
    }
  }

  return null;
}

async function fetchDetails(symbol) {
  const providers = [
    ["fmp", () => fetchFmpDetails(symbol)],
    ["finnhub", () => fetchFinnhubDetails(symbol)],
    ["alphaVantage", () => fetchAlphaVantageDetails(symbol)],
    ["yahoo", () => fetchYahooDetails(symbol)],
  ];
  const merged = {};

  for (const [provider, fetcher] of providers) {
    try {
      mergeDefined(merged, await fetcher());
    } catch (error) {
      console.warn("Details fetch failed", { provider, symbol, message: error.message });
    }
  }

  return Object.keys(merged).length ? merged : null;
}

async function fetchFinnhubQuote(symbol) {
  if (!FINNHUB_API_KEY) return null;
  const url = `https://finnhub.io/api/v1/quote?symbol=${encodeURIComponent(symbol)}&token=${encodeURIComponent(FINNHUB_API_KEY)}`;
  const json = await fetchJson(url);
  const price = num(json && json.c);
  const previous = num(json && json.pc);
  return quoteFromParts({
    symbol,
    price,
    previousClose: previous,
    percentChange: num(json && json.dp),
  });
}

async function fetchFmpQuote(symbol) {
  if (!FMP_API_KEY) return null;
  const url = `https://financialmodelingprep.com/stable/quote?symbol=${encodeURIComponent(symbol)}&apikey=${encodeURIComponent(FMP_API_KEY)}`;
  const json = await fetchJson(url);
  const row = Array.isArray(json) ? json[0] : json;
  const price = num(row && (row.price ?? row.close));
  const previous = num(row && (row.previousClose ?? row.prevClose));
  return quoteFromParts({
    symbol,
    price,
    previousClose: previous,
    percentChange: num(row && (row.changesPercentage ?? row.percentChange ?? row.changePercentage)),
    currency: row && row.currency,
  });
}

async function fetchTwelveDataQuote(symbol) {
  if (!TWELVE_DATA_API_KEY) return null;
  const url = `https://api.twelvedata.com/quote?symbol=${encodeURIComponent(symbol)}&apikey=${encodeURIComponent(TWELVE_DATA_API_KEY)}`;
  const json = await fetchJson(url);
  if (json && json.status === "error") throw new Error(json.message || "Twelve Data error");
  const price = num(json && (json.close ?? json.price));
  const previous = num(json && json.previous_close);
  return quoteFromParts({
    symbol,
    price,
    previousClose: previous,
    percentChange: num(json && json.percent_change),
    currency: json && json.currency,
  });
}

async function fetchAlphaVantageQuote(symbol) {
  if (!ALPHA_VANTAGE_API_KEY) return null;
  const url = `https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=${encodeURIComponent(symbol)}&apikey=${encodeURIComponent(ALPHA_VANTAGE_API_KEY)}`;
  const json = await fetchJson(url);
  if (json && (json.Note || json.Information)) throw new Error(json.Note || json.Information);
  const quote = json && json["Global Quote"];
  const price = num(quote && quote["05. price"]);
  const previous = num(quote && quote["08. previous close"]);
  const rawPct = quote && quote["10. change percent"];
  return quoteFromParts({
    symbol,
    price,
    previousClose: previous,
    percentChange: typeof rawPct === "string" ? num(rawPct.replace("%", "")) : null,
  });
}

async function fetchYahooQuote(symbol) {
  const url = `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(symbol)}?interval=1d&range=5d`;
  const json = await fetchJson(url);
  const result = json && json.chart && json.chart.result && json.chart.result[0];
  const meta = result && result.meta;
  if (!meta) return null;
  const price = num(meta.regularMarketPrice);
  const previous = num(meta.previousClose || meta.chartPreviousClose);
  return quoteFromParts({
    symbol,
    price,
    previousClose: previous,
    currency: meta.currency || null,
  });
}

async function fetchFmpDetails(symbol) {
  if (!FMP_API_KEY) return null;
  const detail = {};
  const quoteUrl = `https://financialmodelingprep.com/stable/quote?symbol=${encodeURIComponent(symbol)}&apikey=${encodeURIComponent(FMP_API_KEY)}`;
  const quoteJson = await fetchJson(quoteUrl);
  const row = Array.isArray(quoteJson) ? quoteJson[0] : quoteJson;
  if (row) {
    detail.fiftyTwoWeekHigh = num(row.yearHigh ?? row.high52Week);
    detail.fiftyTwoWeekLow = num(row.yearLow ?? row.low52Week);
    detail.twoHundredDayAverage = num(row.priceAvg200 ?? row.ma200);
    detail.analystTargetMean = num(row.priceTargetAverage ?? row.targetMeanPrice);
    const volume = num(row.volume);
    const averageVolume = num(row.avgVolume ?? row.averageVolume);
    if (volume && averageVolume) detail.volumeSpikeRatio = volume / averageVolume;
  }

  const earningsUrl = `https://financialmodelingprep.com/stable/earnings?symbol=${encodeURIComponent(symbol)}&limit=8&apikey=${encodeURIComponent(FMP_API_KEY)}`;
  const earningsJson = await fetchJson(earningsUrl);
  const next = nextFutureDate(Array.isArray(earningsJson) ? earningsJson.map((e) => e && e.date) : []);
  if (next) detail.nextEarningsEpochSeconds = next;
  return detail;
}

async function fetchFinnhubDetails(symbol) {
  if (!FINNHUB_API_KEY) return null;
  const today = isoDateInZone(new Date(), NEW_YORK);
  const until = isoDateInZone(new Date(Date.now() + 180 * 86400000), NEW_YORK);
  const url = `https://finnhub.io/api/v1/calendar/earnings?from=${today}&to=${until}&symbol=${encodeURIComponent(symbol)}&token=${encodeURIComponent(FINNHUB_API_KEY)}`;
  const json = await fetchJson(url);
  const dates = Array.isArray(json && json.earningsCalendar)
    ? json.earningsCalendar.map((e) => e && e.date)
    : [];
  const next = nextFutureDate(dates);
  return next ? { nextEarningsEpochSeconds: next } : null;
}

async function fetchAlphaVantageDetails(symbol) {
  if (!ALPHA_VANTAGE_API_KEY) return null;
  const url = `https://www.alphavantage.co/query?function=OVERVIEW&symbol=${encodeURIComponent(symbol)}&apikey=${encodeURIComponent(ALPHA_VANTAGE_API_KEY)}`;
  const json = await fetchJson(url);
  if (json && (json.Note || json.Information)) throw new Error(json.Note || json.Information);
  return {
    fiftyTwoWeekHigh: num(json && json["52WeekHigh"]),
    fiftyTwoWeekLow: num(json && json["52WeekLow"]),
    twoHundredDayAverage: num(json && json["200DayMovingAverage"]),
    analystTargetMean: num(json && json.AnalystTargetPrice),
  };
}

async function fetchYahooDetails(symbol) {
  const modules = "calendarEvents,financialData,summaryDetail,defaultKeyStatistics";
  const url = `https://query1.finance.yahoo.com/v10/finance/quoteSummary/${encodeURIComponent(symbol)}?modules=${modules}`;
  const json = await fetchJson(url);
  const result = json && json.quoteSummary && json.quoteSummary.result && json.quoteSummary.result[0];
  if (!result) return null;
  const volume = raw(result.summaryDetail && result.summaryDetail.volume);
  const averageVolume = raw(result.summaryDetail && result.summaryDetail.averageVolume);
  return {
    nextEarningsEpochSeconds: nextEarnings(result),
    fiftyTwoWeekHigh: raw(result.summaryDetail && result.summaryDetail.fiftyTwoWeekHigh),
    fiftyTwoWeekLow: raw(result.summaryDetail && result.summaryDetail.fiftyTwoWeekLow),
    twoHundredDayAverage: raw(result.summaryDetail && result.summaryDetail.twoHundredDayAverage) ||
      raw(result.defaultKeyStatistics && result.defaultKeyStatistics.twoHundredDayAverage),
    analystTargetMean: raw(result.financialData && result.financialData.targetMeanPrice),
    volumeSpikeRatio: volume && averageVolume ? volume / averageVolume : null,
  };
}

async function fetchJson(url) {
  const response = await fetch(url, {
    headers: {
      Accept: "application/json",
      "User-Agent": "StockWatchdog/1.0 GitHubActionsFreeAlerts",
    },
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

function quoteFromParts({ symbol, price, previousClose, percentChange, currency }) {
  if (!Number.isFinite(price) || price <= 0) return null;
  const previous = Number.isFinite(previousClose) && previousClose > 0 ? previousClose : null;
  const pct = Number.isFinite(percentChange)
    ? percentChange
    : previous
      ? ((price - previous) / previous) * 100
      : null;
  return {
    symbol,
    price,
    previousClose: previous,
    percentChange: pct,
    currency: currency || null,
  };
}

function isValidQuote(quote) {
  return quote && Number.isFinite(quote.price) && quote.price > 0;
}

function mergeDefined(target, source) {
  if (!source) return target;
  Object.entries(source).forEach(([key, value]) => {
    if (value != null && value !== "" && target[key] == null) {
      target[key] = value;
    }
  });
  return target;
}

function nextFutureDate(dates) {
  const now = Date.now();
  return dates
    .map((date) => parseDateEpochSeconds(date))
    .filter((seconds) => seconds && seconds * 1000 >= now - 86400000)
    .sort((a, b) => a - b)[0] || null;
}

function parseDateEpochSeconds(value) {
  if (!value || typeof value !== "string") return null;
  const dateOnly = value.slice(0, 10);
  const millis = Date.parse(`${dateOnly}T12:00:00Z`);
  return Number.isFinite(millis) ? Math.floor(millis / 1000) : null;
}

function nextEarnings(result) {
  const earnings = result.calendarEvents && result.calendarEvents.earnings;
  const dates = earnings && earnings.earningsDate;
  const first = Array.isArray(dates) ? dates[0] : null;
  return raw(first);
}

function raw(value) {
  if (value == null) return null;
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  if (typeof value.raw === "number") return Number.isFinite(value.raw) ? value.raw : null;
  return null;
}

function num(value) {
  if (value == null || value === "" || value === "None" || value === "N/A" || value === "-") return null;
  const n = Number(String(value).replace(/,/g, ""));
  return Number.isFinite(n) ? n : null;
}

function needsDetails(type) {
  return [
    "EARNINGS_REMINDER",
    "FIFTY_TWO_WEEK_HIGH",
    "FIFTY_TWO_WEEK_LOW",
    "MA200_CROSS_UP",
    "MA200_CROSS_DOWN",
    "VOLUME_SPIKE",
    "ANALYST_TARGET_REACH",
  ].includes(String(type || ""));
}

function netPercentVsEntry(currentPrice, entryPrice, platformFeePercent) {
  const fee = Math.max(0, Number(platformFeePercent || 0));
  const netCurrent = currentPrice * (1 - fee / 100);
  return ((netCurrent - entryPrice) / entryPrice) * 100;
}

function isQuietHours(now, settings) {
  if (!settings.quietHoursEnabled) return false;
  const minutes = minutesInZone(now, NAIROBI);
  const start = settings.quietHoursStartMinutes;
  const end = settings.quietHoursEndMinutes;
  if (start === end) return false;
  return start < end
    ? minutes >= start && minutes < end
    : minutes >= start || minutes < end;
}

function isUsMarketOpen(now) {
  const parts = partsInZone(now, NEW_YORK);
  const weekday = parts.weekday;
  if (weekday === "Sat" || weekday === "Sun") return false;
  const minutes = Number(parts.hour) * 60 + Number(parts.minute);
  return minutes >= 9 * 60 + 30 && minutes < 16 * 60;
}

function minutesInZone(now, timeZone) {
  const parts = partsInZone(now, timeZone);
  return Number(parts.hour) * 60 + Number(parts.minute);
}

function isoDateInZone(now, timeZone) {
  const parts = partsInZone(now, timeZone);
  return `${parts.year}-${parts.month}-${parts.day}`;
}

function partsInZone(now, timeZone) {
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone,
    weekday: "short",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  });
  return Object.fromEntries(formatter.formatToParts(now).map((p) => [p.type, p.value]));
}

function normalizeSymbol(symbol) {
  return String(symbol || "").trim().toUpperCase();
}

function fmt(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n.toFixed(2) : "--";
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
