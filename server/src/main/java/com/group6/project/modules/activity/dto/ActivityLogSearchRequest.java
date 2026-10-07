package com.group6.project.modules.activity.dto;

import com.group6.project.common.request.PageRequestDto;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class ActivityLogSearchRequest extends PageRequestDto {
    private UUID entityId;

    public Pageable toPageable() {
        return PageRequest.of(getPage(), getSize(), Sort.by(getDirection(), "occurredAt")
                .and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
