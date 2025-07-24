package com.andersonalexdurante.dto;

import java.util.List;

public record RandomnessOptions(List<TimeOfDay> time_of_day, List<Weather> weather, List<Season> season) {}

