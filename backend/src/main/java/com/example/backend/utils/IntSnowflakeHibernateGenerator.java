package com.example.backend.utils;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;

public class IntSnowflakeHibernateGenerator implements IdentifierGenerator {

    private static final IntSnowflakeIdGenerator snowflake = new IntSnowflakeIdGenerator();

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        return snowflake.nextId();
    }
}