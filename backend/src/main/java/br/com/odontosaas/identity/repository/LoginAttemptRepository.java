package br.com.odontosaas.identity.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LoginAttemptRepository {

    private final JdbcClient jdbc;

    public LoginAttemptRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long countEmailFailures(String email, long windowSeconds) {
        return jdbc.sql("""
                        select count(*) from login_attempts
                        where email = ? and not success and attempted_at > now() - make_interval(secs => ?)
                        """).params(email, windowSeconds).query(Long.class).single();
    }

    public long countIpFailures(String ip, long windowSeconds) {
        return jdbc.sql("""
                                select count(*) from login_attempts
                                where ip = ? and not success and attempted_at > now() - make_interval(secs => ?)
                                """).params(ip, windowSeconds).query(Long.class).single();
    }

    public void insert(String email, String ip, boolean success) {
        jdbc.sql("insert into login_attempts (email, ip, success) values (?, ?, ?)")
                .params(email, ip, success)
                .update();
    }
}
