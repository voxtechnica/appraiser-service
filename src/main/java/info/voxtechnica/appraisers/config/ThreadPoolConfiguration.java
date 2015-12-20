package info.voxtechnica.appraisers.config;

import lombok.Data;

/**
 * Configure the Worker Thread Pool (e.g. for the LicenseService)
 */
@Data
public class ThreadPoolConfiguration {
    private Integer queueSize = 500000;
    private Integer minThreads = 4;
    private Integer maxThreads = 8;
}
