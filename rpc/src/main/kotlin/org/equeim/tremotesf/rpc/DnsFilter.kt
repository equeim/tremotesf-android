package org.equeim.tremotesf.rpc

import okhttp3.Dns
import java.net.InetAddress
import java.net.Inet4Address

class DnsFilter(private val useIPv4Only: Boolean) : Dns {
	override fun lookup(hostname: String): List<InetAddress> {
		var addresses = Dns.SYSTEM.lookup(hostname)

		if (useIPv4Only) {
			addresses = addresses.filter { Inet4Address::class.java.isInstance(it) }
		}

		return addresses
	}
}
