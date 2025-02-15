package com.andersonalexdurante.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record PublishMediaContainerDTO(String creation_id) {
}
