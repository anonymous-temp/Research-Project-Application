package com.faers.agent.dto.responseDto;


import lombok.Data;

@Data
public class Item<T> {


    private String type;


    private T item;





}
