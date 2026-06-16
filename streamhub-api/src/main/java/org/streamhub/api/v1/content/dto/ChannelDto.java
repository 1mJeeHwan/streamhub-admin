package org.streamhub.api.v1.content.dto;

import org.streamhub.api.v1.content.entity.Channel;

/**
 * Channel option for the content form's channel selector.
 *
 * @param id   channel id
 * @param name channel name
 */
public record ChannelDto(Long id, String name) {

    public static ChannelDto from(Channel channel) {
        return new ChannelDto(channel.getId(), channel.getName());
    }
}
