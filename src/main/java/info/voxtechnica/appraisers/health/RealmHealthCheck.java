package info.voxtechnica.appraisers.health;

import com.codahale.metrics.health.HealthCheck;
import info.voxtechnica.appraisers.config.RealmConfiguration;

public class RealmHealthCheck extends HealthCheck {
    private final RealmConfiguration realm;

    public RealmHealthCheck(RealmConfiguration realm) {
        super();
        this.realm = realm;
    }

    @Override
    protected Result check() throws Exception {
        return Result.healthy("realm=" + realm.getRealmName() + " apiUsers=" + realm.getApiUsers().size());
    }

}
