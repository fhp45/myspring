package fhp.service;

import fhp.annotation.Service;

import java.util.UUID;

@Service
public class MyService {

    public String getByName(String name){
        return UUID.randomUUID().toString()+name;
    }

}
