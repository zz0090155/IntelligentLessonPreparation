package com.example.backend.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtils {
    static final String jwtToken = "abcd1234!@#$%^&*()_+~{}[]|<>?;:'\",./";

    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
            jwtToken.getBytes(StandardCharsets.UTF_8)
    );

    public static String createToken(int id){
        Map<String,Object> claims=new HashMap<>();
        claims.put("id",id);
        JwtBuilder jwtBuilder= Jwts.builder()
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis()+24*60*60*1000));
        String token=jwtBuilder.compact();
        return token;
    }

    public static int getIdFromJwt(String token){
        Claims claims=Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();
        return (int) claims.get("id");
    }

    public static boolean checkToken(String token){
        try{
            Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token);
            return true;
        }catch (Exception e){
            return false;
        }
    }
}