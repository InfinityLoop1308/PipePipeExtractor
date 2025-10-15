package project.pipepipe.extractor.services.youtube.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.services.youtube.YouTubeLinks.BROWSE_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.RESOLVE_CHANNEL_ID_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.TAB_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.WEB_HEADER
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getChannelIdBody
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getChannelInfoBody
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getContinuationBody
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeChannelIdParser.parseChannelId
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeStreamInfoDataParser.parseVideoRenderer
import project.pipepipe.extractor.utils.parseNumberWithSuffix
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.shared.infoitem.ChannelTabInfo
import project.pipepipe.shared.infoitem.ChannelTabType
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.*
import project.pipepipe.extractor.utils.RequestHelper.replaceQueryValue
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString
import java.net.URLDecoder
import java.net.URLEncoder

class YouTubeChannelMainTabExtractor(
    url: String,
) : Extractor<ChannelInfo, StreamInfo>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            val id = parseChannelId(url)!!
            if (url.contains("/channel/")) {
                return JobStepResult.ContinueWith(
                    listOf(
                        ClientTask(
                            payload = Payload(
                                RequestMethod.POST,
                                BROWSE_URL,
                                WEB_HEADER,
                                getChannelInfoBody(id, ChannelTabType.VIDEOS)
                            )
                        )
                    ), PlainState(1)
                )
            } else {
                return JobStepResult.ContinueWith(
                    listOf(
                        ClientTask(
                            payload = Payload(
                                RequestMethod.POST,
                                RESOLVE_CHANNEL_ID_URL,
                                WEB_HEADER,
                                getChannelIdBody(url)
                            )
                        )
                    ), PlainState(0)
                )
            }
        } else if (currentState.step == 0) {
            val result = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val id = result.requireString("/endpoint/browseEndpoint/browseId")
            return JobStepResult.ContinueWith(listOf(
                ClientTask(payload = Payload(
                    RequestMethod.POST,
                    BROWSE_URL,
                    WEB_HEADER,
                    getChannelInfoBody(id, ChannelTabType.VIDEOS)
                ))
            ), PlainState(1))
        } else if (currentState.step == 1) {
            val result = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val name = result.requireString("/metadata/channelMetadataRenderer/title")
            val nameEncoded = URLEncoder.encode(name, "UTF-8")
            val tabs = mutableListOf<ChannelTabInfo>()
            var nextPageUrl: String? = null
            result.requireArray("/contents/twoColumnBrowseResultsRenderer/tabs").forEach {
                val id = runCatching { it.requireString("/tabRenderer/endpoint/browseEndpoint/browseId") }.getOrNull()
                when (runCatching{ it.requireString("/tabRenderer/title") }.getOrNull()) {
                     "Videos" -> {
                         tabs.add(ChannelTabInfo(
                             url = "$TAB_RAW_URL?id=$id&type=videos&name=$nameEncoded",
                             type = ChannelTabType.VIDEOS
                         ))
                         it.requireArray("/tabRenderer/content/richGridRenderer/contents").mapNotNull {
                             runCatching{ it.requireObject("/richItemRenderer/content") }.getOrNull()?.let {
                                 commit { parseVideoRenderer(it, name, id) }
                             }
                         }
                         runCatching{
                             it.requireArray("/tabRenderer/content/richGridRenderer/contents")
                                 .last()
                                 .requireString("/continuationItemRenderer/continuationEndpoint/continuationCommand/token")
                         }.getOrNull()?.let {
                             nextPageUrl = "$TAB_RAW_URL?id=$id&type=videos&continuation=$it&name=$nameEncoded"
                         }
                     }
                    "Shorts" -> {} //todo
                    "Live" -> {
                        tabs.add(ChannelTabInfo(
                            url = "$TAB_RAW_URL?id=$id&type=lives&name=$nameEncoded",
                            type = ChannelTabType.LIVES
                        ))
                    }
                    "Playlists" -> {
                        tabs.add(ChannelTabInfo(
                            url = "$TAB_RAW_URL?id=$id&type=playlists&name=$nameEncoded",
                            type = ChannelTabType.PLAYLISTS
                        ))
                    }
                }
            }
            return JobStepResult.CompleteWith(ExtractResult(
                info = ChannelInfo(
                    url = result.requireString("/metadata/channelMetadataRenderer/channelUrl"),
                    name = name,
                    serviceId = "YOUTUBE",
                    thumbnailUrl = result.requireArray("/metadata/channelMetadataRenderer/avatar/thumbnails").last().requireString("url"),
                    bannerUrl = runCatching{ result.requireArray("/header/pageHeaderRenderer/content/pageHeaderViewModel/banner/imageBannerViewModel/image/sources").last().requireString("url") }.getOrNull(),
                    feedUrl = null,
                    description = result.requireString("/metadata/channelMetadataRenderer/description"),
                    subscriberCount = safeGet{
                        parseNumberWithSuffix(
                            result.requireArray("/header/pageHeaderRenderer/content/pageHeaderViewModel/metadata/contentMetadataViewModel/metadataRows")
                                .first { it.requireString("/metadataParts/0/text/content").contains("subscribers") }
                                .requireString("/metadataParts/0/text/content")
                        )
                    },
                    isVerified = false,
                    tabs = tabs
                ),
                errors = errors,
                pagedData = PagedData(
                    itemList = itemList,
                    nextPageUrl = nextPageUrl
                )
            ))
        } else throw IllegalStateException()
    }

    override suspend fun fetchGivenPage(
        url: String,
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            RequestMethod.POST,
                            BROWSE_URL,
                            WEB_HEADER,
                            getContinuationBody(getQueryValue(url, "continuation")!!)
                        )
                    )
                ), PlainState(0)
            )
        } else {
            val result = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            var nextPageUrl: String? = null
            result.requireArray("/onResponseReceivedActions/0/appendContinuationItemsAction/continuationItems").forEach {
                val videoRenderer = runCatching { it.requireObject("/richItemRenderer/content") }.getOrNull()
                if (videoRenderer != null) {
                    commit { parseVideoRenderer(
                        data = videoRenderer,
                        overrideChannelName = URLDecoder.decode(getQueryValue(url, "name"), "UTF-8"),
                        overrideChannelId = getQueryValue(url, "id")
                    ) }
                } else { //continuation item
                    nextPageUrl = replaceQueryValue(url, "continuation", it.requireString("/continuationItemRenderer/continuationEndpoint/continuationCommand/token"))
                }
            }
            return JobStepResult.CompleteWith(ExtractResult(
                errors = errors,
                pagedData = PagedData(
                    itemList = itemList,
                    nextPageUrl = nextPageUrl
                )
            ))
        }
    }
}