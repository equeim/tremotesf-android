package org.equeim.tremotesf.billing

import android.net.Uri

private const val PAYPAL_USER = "DDQTRHTY5YV2G"
private const val PAYPAL_CURRENCY_CODE = "USD"
private const val PAYPAL_ITEM_NAME = "Support Tremotesf (Android) development"

val DONATE_PAYPAL_URI: Uri by lazy(LazyThreadSafetyMode.PUBLICATION) {
    Uri.Builder()
        .scheme("https")
        .authority("www.paypal.com")
        .path("cgi-bin/webscr")
        .appendQueryParameter("cmd", "_donations")
        .appendQueryParameter("business", PAYPAL_USER)
        .appendQueryParameter("lc", "US")
        .appendQueryParameter("item_name", PAYPAL_ITEM_NAME)
        .appendQueryParameter("no_note", "1")
        .appendQueryParameter("no_shipping", "1")
        .appendQueryParameter("currency_code", PAYPAL_CURRENCY_CODE)
        .build()
}

val DONATE_YANDEX_URI: Uri by lazy(LazyThreadSafetyMode.PUBLICATION) {
    Uri.parse("https://yasobe.ru/na/equeim_tremotesf_android")
}
