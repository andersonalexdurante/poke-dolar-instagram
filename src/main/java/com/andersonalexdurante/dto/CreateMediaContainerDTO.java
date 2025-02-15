package com.andersonalexdurante.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CreateMediaContainerDTO(String image_url, String caption) {
}
