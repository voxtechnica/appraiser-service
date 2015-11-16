package info.voxtechnica.appraisers.config;

import com.google.common.cache.CacheBuilderSpec;
import info.voxtechnica.appraisers.model.User;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Security Realm Configuration for the ApiUserAuthenticator
 */
@Data
public class RealmConfiguration {
    private String realmName = "VoxTechnica";
    private long passcodeRequestInterval = 60; // 1 minute
    private long passcodeExpiration = 900; // 15 minutes
    private long passwordExpiration = 0; // never
    private long tokenExpiration = 2628000; // one month (1/12 of a year's seconds)
    private List<User> apiUsers = new ArrayList<>();
    private CacheBuilderSpec authenticationCachePolicy;
}
