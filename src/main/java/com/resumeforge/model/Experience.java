package com.resumeforge.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Experience(
    String title,
    String company,
    String startDate,
    String endDate,
    String summary,
    List<String> bullets
) {
}
