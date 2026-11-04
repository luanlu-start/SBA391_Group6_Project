package com.fptpost.modules.catalog.dto.internal;

import com.fptpost.modules.catalog.enums.ParameterValueType;

public record ParameterDefinition(String prmId, String key, String group, String name,
                                  ParameterValueType valueType, String defaultValue, String unit,
                                  String description) { }
