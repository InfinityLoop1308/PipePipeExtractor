package project.pipepipe.extractor.services.niconico.extractor

import org.jsoup.Jsoup
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.TAB_RAW_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.USER_URL
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.GOOGLE_HEADER
import project.pipepipe.extractor.services.niconico.dataparser.NicoNicoStreamInfoDataParser.parseFromRSSXml
import project.pipepipe.extractor.utils.incrementUrlParam
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.shared.infoitem.ChannelTabInfo
import project.pipepipe.shared.infoitem.ChannelTabType
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireString
import java.net.URLDecoder
import java.net.URLEncoder

class NicoNicoChannelMainTabExtractor(url: String) : Extractor<ChannelInfo, StreamInfo>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val safeUrl = url.substringBefore("?")
        val userId = getQueryValue(url, "id") ?: safeUrl.substringAfterLast("/")

        if (currentState == null) {
            val userUrl = if (url.contains(TAB_RAW_URL)) {
                "https://www.nicovideo.jp/user/$userId"
            } else {
                safeUrl
            }
            return JobStepResult.ContinueWith(listOf(
                ClientTask("info", Payload(RequestMethod.GET, userUrl, GOOGLE_HEADER)),
                ClientTask("videos", Payload(RequestMethod.GET, "$userUrl/video?rss=2.0&page=1", GOOGLE_HEADER))
            ), PlainState(1))
        } else {
            val infoData = ExtractorContext.objectMapper.readTree(
                Jsoup.parse(clientResults!!.first { it.taskId == "info" }.result!!)
                .getElementById("js-initial-userpage-data")!!
                .attr("data-initial-data")
            )
            val userVideoData = Jsoup.parse(clientResults.first { it.taskId == "videos" }.result!!)
            val channelName = infoData.requireString("/state/userDetails/userDetails/user/nickname")
            val channelId = infoData.requireString("/state/userDetails/userDetails/user/id")
            val nameEncoded = URLEncoder.encode(channelName, "UTF-8")

            userVideoData.select("item").forEach {
                commit { parseFromRSSXml(it, channelName, USER_URL + channelId) }
            }

            val tabs = mutableListOf(
                ChannelTabInfo("$TAB_RAW_URL?id=$channelId&type=videos&name=$nameEncoded", ChannelTabType.VIDEOS)
            )

            tabs.add(ChannelTabInfo(
                url = "$TAB_RAW_URL?id=$userId&type=playlists&name=$nameEncoded",
                type = ChannelTabType.PLAYLISTS
            ))

            tabs.add(ChannelTabInfo(
                url = "$TAB_RAW_URL?id=$userId&type=albums&name=$nameEncoded",
                type = ChannelTabType.ALBUMS
            ))

            return JobStepResult.CompleteWith(ExtractResult(
                info = ChannelInfo(
                    url = "https://www.nicovideo.jp/user/$userId",
                    name = channelName,
                    serviceId = "NICONICO",
                    thumbnailUrl = infoData.requireString("/state/userDetails/userDetails/user/icons/large"),
                    subscriberCount = infoData.requireLong("/state/userDetails/userDetails/user/followerCount"),
                    description = infoData.requireString("/state/userDetails/userDetails/user/description"),
                    tabs = tabs
                ),
                errors = errors,
                pagedData = PagedData(itemList, "$TAB_RAW_URL?id=$userId&type=videos&page=2&name=$nameEncoded")
            ))
        }
    }

    override suspend fun fetchGivenPage(
        url: String,
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val userId = getQueryValue(url, "id")!!
        val page = getQueryValue(url, "page") ?: "1"
        val channelName = URLDecoder.decode(getQueryValue(url, "name")!!, "UTF-8")

        if (currentState == null) {
            val realUrl = "https://www.nicovideo.jp/user/$userId/video?rss=2.0&page=$page"
            return JobStepResult.ContinueWith(listOf(
                ClientTask("videos", Payload(RequestMethod.GET, realUrl, GOOGLE_HEADER))
            ), PlainState(1))
        } else {
            val userVideoData = Jsoup.parse(clientResults!!.first { it.taskId == "videos" }.result!!)
            userVideoData.select("item").forEach {
                commit { parseFromRSSXml(it, channelName, USER_URL + userId) }
            }
            val nextPageUrl = if (itemList.isNotEmpty()) url.incrementUrlParam("page") else null
            return JobStepResult.CompleteWith(ExtractResult(
                errors = errors,
                pagedData = PagedData(itemList, nextPageUrl)
            ))
        }
    }
}