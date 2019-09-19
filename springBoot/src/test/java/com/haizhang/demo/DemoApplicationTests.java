package com.haizhang.demo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.locks.ReentrantLock;

@RunWith(SpringRunner.class)
@ComponentScan(value ={"com"} ,includeFilters = {@ComponentScan.Filter(type = FilterType.ANNOTATION,classes = Service.class)},useDefaultFilters = false)
@SpringBootTest
public class DemoApplicationTests {

    @Autowired
    RedisTemplate<String,Object> redisTemplate;
    @Test
    public void contextLoads() {
        ValueOperations<String, Object> stringObjectValueOperations = redisTemplate.opsForValue();
        stringObjectValueOperations.set("name","haizhang");
        String name = (String)stringObjectValueOperations.get("name");
        System.out.println(name);
    }
ReentrantLock
    @Test
    public void testHash(){
        HashOperations<String, Object, Object> stringObjectObjectHashOperations = redisTemplate.opsForHash();
        stringObjectObjectHashOperations.put("user","name","luohaizhangh");
        String name =(String) stringObjectObjectHashOperations.get("user","name");
        System.out.println(name);
    }

}
