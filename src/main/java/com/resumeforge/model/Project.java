package com.resumeforge.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Project(
    String title,
    String description,
    String startDate,
    String endDate,
    List<String> technologies,
    String link,
    String details
) {
}
