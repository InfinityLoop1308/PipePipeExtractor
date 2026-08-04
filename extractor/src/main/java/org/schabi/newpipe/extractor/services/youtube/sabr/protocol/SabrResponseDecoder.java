package org.schabi.newpipe.extractor.services.youtube.sabr.protocol;

import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrResponse;
import org.schabi.newpipe.extractor.services.youtube.sabr.generated.SabrFormatInitializationMetadata;
import org.schabi.newpipe.extractor.services.youtube.sabr.generated.SabrMediaHeader;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.UmpReader.UmpPart;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public final class SabrResponseDecoder {
    public static final int ONESIE_HEADER = 10;
    public static final int ONESIE_DATA = 11;
    public static final int ONESIE_ENCRYPTED_MEDIA = 12;
    public static final int MEDIA_HEADER = 20;
    public static final int MEDIA = 21;
    public static final int MEDIA_END = 22;
    public static final int CONFIG = 30;
    public static final int LIVE_METADATA = 31;
    public static final int HOSTNAME_CHANGE_HINT_DEPRECATED = 32;
    public static final int LIVE_METADATA_PROMISE = 33;
    public static final int LIVE_METADATA_PROMISE_CANCELLATION = 34;
    public static final int NEXT_REQUEST_POLICY = 35;
    public static final int USTREAMER_VIDEO_AND_FORMAT_METADATA = 36;
    public static final int FORMAT_SELECTION_CONFIG = 37;
    public static final int USTREAMER_SELECTED_MEDIA_STREAM = 38;
    public static final int FORMAT_INITIALIZATION_METADATA = 42;
    public static final int SABR_REDIRECT = 43;
    public static final int SABR_ERROR = 44;
    public static final int SABR_SEEK = 45;
    public static final int RELOAD_PLAYER_RESPONSE = 46;
    public static final int PLAYBACK_START_POLICY = 47;
    public static final int ALLOWED_CACHED_FORMATS = 48;
    public static final int START_BW_SAMPLING_HINT = 49;
    public static final int PAUSE_BW_SAMPLING_HINT = 50;
    public static final int SELECTABLE_FORMATS = 51;
    public static final int REQUEST_IDENTIFIER = 52;
    public static final int REQUEST_CANCELLATION_POLICY = 53;
    public static final int ONESIE_PREFETCH_REJECTION = 54;
    public static final int TIMELINE_CONTEXT = 55;
    public static final int REQUEST_PIPELINING = 56;
    public static final int SABR_CONTEXT_UPDATE = 57;
    public static final int STREAM_PROTECTION_STATUS = 58;
    public static final int SABR_CONTEXT_SENDING_POLICY = 59;
    public static final int LAWNMOWER_POLICY = 60;
    public static final int SABR_ACK = 61;
    public static final int END_OF_TRACK = 62;
    public static final int CACHE_LOAD_POLICY = 63;
    public static final int LAWNMOWER_MESSAGING_POLICY = 64;
    public static final int PREWARM_CONNECTION = 65;
    public static final int PLAYBACK_DEBUG_INFO = 66;
    public static final int SNACKBAR_MESSAGE = 67;

    private SabrResponseDecoder() {
    }

    @Nonnull
    public static YoutubeSabrResponse decode(@Nonnull final byte[] data)
            throws SabrProtocolException {
        return decodeParts(UmpReader.readAll(data));
    }

    /**
     * Decode an already-parsed list of UMP parts. Used by the streaming path, which collects the
     * small control parts (everything except the big MEDIA payloads) and decodes them here, while
     * the MEDIA segments are assembled separately so the whole body is never held at once.
     */
    @Nonnull
    public static YoutubeSabrResponse decodeParts(@Nonnull final List<UmpPart> parts)
            throws SabrProtocolException {
        final YoutubeSabrResponse decoded = new YoutubeSabrResponse();
        for (final UmpPart part : parts) {
            final byte[] partData = part.getRawData();
            decoded.addPart(part);
            if (part.getType() != MEDIA && part.getType() != MEDIA_END) {
                try {
                    decoded.addWireFieldSummary(part.getType(),
                            SabrProto.summarizeFields(partData));
                } catch (final SabrProtocolException ignored) {
                    decoded.addWireFieldSummary(part.getType(),
                            "opaqueBytes=" + partData.length);
                }
            }
            try {
                if (part.getType() == MEDIA_HEADER) {
                    decoded.addMediaHeader(SabrMediaHeader.decode(partData));
                    continue;
                }
                if (part.getType() == MEDIA) {
                    if (partData.length > 0) {
                        decoded.addMediaBytes(partData[0] & 0xff, partData.length - 1L);
                    }
                    continue;
                }
                if (part.getType() == MEDIA_END) {
                    if (partData.length > 0) decoded.addMediaEndHeaderId(partData[0] & 0xff);
                    continue;
                }
                switch (part.getType()) {
                case ONESIE_HEADER:
                case ONESIE_DATA:
                case ONESIE_ENCRYPTED_MEDIA:
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case FORMAT_INITIALIZATION_METADATA:
                    final SabrFormatInitializationMetadata metadata =
                            SabrFormatInitializationMetadata.decode(partData);
                    decoded.addFormatInitializationMetadata(metadata);
                    decoded.addGenericPartDescription(part.getType(), metadata.summarize());
                    break;
                case MEDIA_HEADER:
                    decoded.addMediaHeader(SabrMediaHeader.decode(partData));
                    break;
                case MEDIA:
                    if (partData.length > 0) {
                        decoded.addMediaBytes(partData[0] & 0xff, partData.length - 1L);
                    }
                    break;
                case MEDIA_END:
                    if (partData.length > 0) {
                        decoded.addMediaEndHeaderId(partData[0] & 0xff);
                    }
                    break;
                case LIVE_METADATA:
                    decoded.addLiveMetadata(partData);
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case NEXT_REQUEST_POLICY:
                    decodeNextRequestPolicy(partData, decoded);
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case SABR_REDIRECT:
                    decoded.setRedirectUrl(readString(partData, 1));
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case SABR_SEEK:
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case SABR_ERROR:
                    final String error = decodeError(partData);
                    decoded.setSabrError(error);
                    decoded.addGenericPartDescription(part.getType(), error);
                    break;
                case RELOAD_PLAYER_RESPONSE:
                    decoded.setReloadRequested(true);
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case STREAM_PROTECTION_STATUS:
                    decodeStreamProtectionStatus(partData, decoded);
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case PLAYBACK_START_POLICY:
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case SABR_CONTEXT_UPDATE:
                    decoded.addSabrContextUpdate(partData);
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case SABR_CONTEXT_SENDING_POLICY:
                    decoded.setSabrContextSendingPolicy(partData);
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case SNACKBAR_MESSAGE:
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case FORMAT_SELECTION_CONFIG:
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case PREWARM_CONNECTION:
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case START_BW_SAMPLING_HINT:
                case CONFIG:
                case HOSTNAME_CHANGE_HINT_DEPRECATED:
                case LIVE_METADATA_PROMISE:
                case LIVE_METADATA_PROMISE_CANCELLATION:
                case USTREAMER_VIDEO_AND_FORMAT_METADATA:
                case USTREAMER_SELECTED_MEDIA_STREAM:
                case ALLOWED_CACHED_FORMATS:
                case PAUSE_BW_SAMPLING_HINT:
                case ONESIE_PREFETCH_REJECTION:
                case TIMELINE_CONTEXT:
                case REQUEST_PIPELINING:
                case LAWNMOWER_POLICY:
                case SABR_ACK:
                case END_OF_TRACK:
                case CACHE_LOAD_POLICY:
                case LAWNMOWER_MESSAGING_POLICY:
                case PLAYBACK_DEBUG_INFO:
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case REQUEST_IDENTIFIER:
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case REQUEST_CANCELLATION_POLICY:
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                case SELECTABLE_FORMATS:
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                default:
                    decoded.addUnknownPartType(part.getType());
                    decoded.addGenericPartDescription(part.getType(),
                            describeGenericMessage(partData));
                    break;
                }
            } catch (final SabrProtocolException e) {
                // One malformed protobuf message must not discard valid MEDIA from the rest of the
                // UMP response. Wire types 6/7 are invalid protobuf, and have been observed in a
                // transient NEXT_REQUEST_POLICY response. Ignore only that part and retain a
                // bounded diagnostic. MEDIA_HEADER corruption is still detected by the media
                // integrity checks (media-without-header) and goes through bounded recovery.
                decoded.addMalformedPart(part.getType(), part.getSize(), e);
            }
        }
        return decoded;
    }

    private static void decodeNextRequestPolicy(@Nonnull final byte[] data,
                                                @Nonnull final YoutubeSabrResponse decoded)
            throws SabrProtocolException {
        decoded.setNextRequestPolicy(data);
        for (final SabrProto.Field field : SabrProto.readFields(data)) {
            if (field.getNumber() == 4 && field.getWireType() == SabrProto.WIRE_VARINT) {
                decoded.setBackoffTimeMs((int) field.getVarint());
            }
        }
    }

    private static void decodeStreamProtectionStatus(@Nonnull final byte[] data,
                                                       @Nonnull final YoutubeSabrResponse decoded)
            throws SabrProtocolException {
        for (final SabrProto.Field field : SabrProto.readFields(data)) {
            if (field.getNumber() == 1 && field.getWireType() == SabrProto.WIRE_VARINT) {
                decoded.setStreamProtectionStatus((int) field.getVarint());
            } else if (field.getNumber() == 2
                    && field.getWireType() == SabrProto.WIRE_VARINT) {
                decoded.setStreamProtectionMaxRetries((int) field.getVarint());
            }
        }
    }

    @Nullable
    private static String readString(@Nonnull final byte[] data, final int number)
            throws SabrProtocolException {
        String value = null;
        for (final SabrProto.Field field : SabrProto.readFields(data)) {
            if (field.getNumber() == number
                    && field.getWireType() == SabrProto.WIRE_LENGTH_DELIMITED) {
                value = field.getString();
            }
        }
        return value;
    }

    @Nonnull
    private static String decodeError(@Nonnull final byte[] data) throws SabrProtocolException {
        String type = null;
        int code = 0;
        for (final SabrProto.Field field : SabrProto.readFields(data)) {
            if (field.getNumber() == 1
                    && field.getWireType() == SabrProto.WIRE_LENGTH_DELIMITED) {
                type = field.getString();
            } else if (field.getNumber() == 2
                    && field.getWireType() == SabrProto.WIRE_VARINT) {
                code = (int) field.getVarint();
            }
        }
        return "type=" + (type == null ? "null" : type) + ", code=" + code;
    }

    @Nonnull
    private static String describeGenericMessage(@Nonnull final byte[] data) {
        try {
            return SabrProto.summarizeFields(data);
        } catch (final Exception e) {
            return "undecodable(" + data.length + " bytes)";
        }
    }
}
