package raju.shingadiya.juniper

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
