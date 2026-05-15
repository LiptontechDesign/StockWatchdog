const admin = require("firebase-admin");

const ALERT_USERS = "alertUsers";
const NAIROBI = "Africa/Nairobi";
const NEW_YORK = "America/New_York";
const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "stockwatchdog-41541";
const DRY_RUN = process.argv.includes("--dry-run");

main().catch((error) => {
  console.error("Cloud alert run failed", error);
  process.exitCode = 1;
});

async function main() {
  initializeFirebase();
  const db = admin.firestore();
  const users = await db.collection(ALERT_USERS).where("active", "==", true).get();
  console.log(`Checking ${users.size} active cloud alert user(s)`);

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
  try {
    const url = `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(symbol)}?interval=1d&range=5d`;
    const json = await fetchJson(url);
    const result = json && json.chart && json.chart.result && json.chart.result[0];
    const meta = result && result.meta;
    if (!meta) return null;
    const price = Number(meta.regularMarketPrice);
    const previous = Number(meta.previousClose || meta.chartPreviousClose);
    if (!Number.isFinite(price) || price <= 0) return null;
    return {
      symbol,
      price,
      previousClose: Number.isFinite(previous) && previous > 0 ? previous : null,
      percentChange: Number.isFinite(previous) && previous > 0 ? ((price - previous) / previous) * 100 : null,
      currency: meta.currency || null,
    };
  } catch (error) {
    console.warn("Quote fetch failed", { symbol, message: error.message });
    return null;
  }
}

async function fetchDetails(symbol) {
  try {
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
  } catch (error) {
    console.warn("Details fetch failed", { symbol, message: error.message });
    return null;
  }
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
