package com.example.backend.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;


public class ErrorResult {

   public static Map<String,Object> error(String detail){
       Map map =new HashMap();
       map.put("detail",detail);
       return map;
   }
}