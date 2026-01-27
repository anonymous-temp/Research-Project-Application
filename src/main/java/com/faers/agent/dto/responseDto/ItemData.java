package com.faers.agent.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public  class ItemData{

        private String  analysis;

        private List<String> todo;

        private List<String> todo_details;

    }