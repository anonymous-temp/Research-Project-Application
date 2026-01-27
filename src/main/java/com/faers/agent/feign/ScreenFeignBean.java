package com.faers.agent.feign;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class ScreenFeignBean {
    @Autowired
    private FineScreenFeign fineScreenFeign;
    public static FineScreenFeign screenFeign;

    @PostConstruct
    public void getFineScreenFeign(){
        screenFeign = this.fineScreenFeign;
    }
}
