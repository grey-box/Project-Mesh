package com.greybox.projectmesh.util

/**
 * Strict dotted-quad IPv4 test — the single predicate for "is this an address?".
 * Pure (no DNS), shared by [AppViewModel.applyDeepLink]
 * and the chatScreen route, so both agree on what counts as an IP.
 */

private val IPV4_REGEX = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\$")
fun isIpv4(value: String): Boolean = IPV4_REGEX.matches(value)
