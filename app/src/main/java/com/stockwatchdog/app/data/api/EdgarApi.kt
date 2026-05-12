package com.stockwatchdog.app.data.api

import com.stockwatchdog.app.data.api.models.EdgarCompanyFacts
import com.stockwatchdog.app.data.api.models.EdgarTickerCompany
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface EdgarApi {

    @GET
    suspend fun companyTickers(
        @Url url: String = "https://www.sec.gov/files/company_tickers.json"
    ): Map<String, EdgarTickerCompany>

    @GET("api/xbrl/companyfacts/CIK{cik}.json")
    suspend fun companyFacts(
        @Path("cik") cik: String
    ): EdgarCompanyFacts
}
