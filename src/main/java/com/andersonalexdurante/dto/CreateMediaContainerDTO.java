package com.andersonalexdurante.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CreateMediaContainerDTO(String video_url, String caption, String media_type) {
    public CreateMediaContainerDTO(String video_url, String caption) {
        this(video_url, caption, "REELS");
    }
}