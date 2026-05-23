package com.mylife.pacs.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "dicom.pacs")
public class DicomPacsProperties {

    @NotBlank
    private String localAet = "MY_PACS";

    @Min(0)
    private int localPort = 11112;

    @NotBlank
    private String storageDir = ".data\\storage";

    @NotBlank
    private String filePathPattern = "{00100020}\\{0020000D}\\{0020000E}\\{00080018}.dcm";

    @NotBlank
    private String storageProvider = "local";

    private long idleTimeout = 1_200_000L;

    @NotBlank
    private String dbType = "h2";

    private final Netty netty = new Netty();
    private final Scu scu = new Scu();
    private final Scp scp = new Scp();
    private final Security security = new Security();
    private final Dicomweb dicomweb = new Dicomweb();
    private final Sync sync = new Sync();
    private final S3 s3 = new S3();
    private final FastDfs fastdfs = new FastDfs();

    public String getLocalAet() {
        return localAet;
    }

    public void setLocalAet(String localAet) {
        this.localAet = localAet;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public String getFilePathPattern() {
        return filePathPattern;
    }

    public void setFilePathPattern(String filePathPattern) {
        this.filePathPattern = filePathPattern;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public Netty getNetty() {
        return netty;
    }

    public Scu getScu() {
        return scu;
    }

    public Scp getScp() {
        return scp;
    }

    public Security getSecurity() {
        return security;
    }

    public Dicomweb getDicomweb() {
        return dicomweb;
    }

    public Sync getSync() {
        return sync;
    }

    public S3 getS3() {
        return s3;
    }

    public FastDfs getFastdfs() {
        return fastdfs;
    }

    public static class Netty {
        private int bossThreads = 1;
        private int workerThreads = 0;
        private int maxConnections = 1_000;
        private String sendBufferSize = "1MB";
        private String receiveBufferSize = "1MB";

        public int getBossThreads() {
            return bossThreads;
        }

        public void setBossThreads(int bossThreads) {
            this.bossThreads = bossThreads;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public String getSendBufferSize() {
            return sendBufferSize;
        }

        public void setSendBufferSize(String sendBufferSize) {
            this.sendBufferSize = sendBufferSize;
        }

        public String getReceiveBufferSize() {
            return receiveBufferSize;
        }

        public void setReceiveBufferSize(String receiveBufferSize) {
            this.receiveBufferSize = receiveBufferSize;
        }
    }

    public static class Scu {
        private boolean enabled = true;
        private int findRetryCount = 10;
        private int connectTimeout = 10_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getFindRetryCount() {
            return findRetryCount;
        }

        public void setFindRetryCount(int findRetryCount) {
            this.findRetryCount = findRetryCount;
        }

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }
    }

    public static class Scp {
        private boolean enabled = true;
        private boolean findEnabled = true;
        private boolean moveEnabled = true;
        private boolean storeEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFindEnabled() {
            return findEnabled;
        }

        public void setFindEnabled(boolean findEnabled) {
            this.findEnabled = findEnabled;
        }

        public boolean isMoveEnabled() {
            return moveEnabled;
        }

        public void setMoveEnabled(boolean moveEnabled) {
            this.moveEnabled = moveEnabled;
        }

        public boolean isStoreEnabled() {
            return storeEnabled;
        }

        public void setStoreEnabled(boolean storeEnabled) {
            this.storeEnabled = storeEnabled;
        }
    }

    public static class Security {
        private boolean tlsEnabled;
        private String keystorePath;
        private String truststorePath;
        private List<String> aetWhitelist = new ArrayList<>();

        public boolean isTlsEnabled() {
            return tlsEnabled;
        }

        public void setTlsEnabled(boolean tlsEnabled) {
            this.tlsEnabled = tlsEnabled;
        }

        public String getKeystorePath() {
            return keystorePath;
        }

        public void setKeystorePath(String keystorePath) {
            this.keystorePath = keystorePath;
        }

        public String getTruststorePath() {
            return truststorePath;
        }

        public void setTruststorePath(String truststorePath) {
            this.truststorePath = truststorePath;
        }

        public List<String> getAetWhitelist() {
            return aetWhitelist;
        }

        public void setAetWhitelist(List<String> aetWhitelist) {
            this.aetWhitelist = aetWhitelist;
        }
    }

    public static class Dicomweb {
        private boolean authEnabled = true;
        private String jwtSecret = "change-me";

        public boolean isAuthEnabled() {
            return authEnabled;
        }

        public void setAuthEnabled(boolean authEnabled) {
            this.authEnabled = authEnabled;
        }

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }
    }

    public static class Sync {
        private boolean enabled;
        private long dispatcherIntervalMs = 60_000L;
        private long initialDelayMs = 30_000L;
        private long lockAtMostMs = 120_000L;
        private String lockProvider = "database";
        private List<PullJob> pullJobs = new ArrayList<>();
        private List<PushJob> pushJobs = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDispatcherIntervalMs() {
            return dispatcherIntervalMs;
        }

        public void setDispatcherIntervalMs(long dispatcherIntervalMs) {
            this.dispatcherIntervalMs = dispatcherIntervalMs;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public long getLockAtMostMs() {
            return lockAtMostMs;
        }

        public void setLockAtMostMs(long lockAtMostMs) {
            this.lockAtMostMs = lockAtMostMs;
        }

        public String getLockProvider() {
            return lockProvider;
        }

        public void setLockProvider(String lockProvider) {
            this.lockProvider = lockProvider;
        }

        public List<PullJob> getPullJobs() {
            return pullJobs;
        }

        public void setPullJobs(List<PullJob> pullJobs) {
            this.pullJobs = pullJobs == null ? new ArrayList<>() : pullJobs;
        }

        public List<PushJob> getPushJobs() {
            return pushJobs;
        }

        public void setPushJobs(List<PushJob> pushJobs) {
            this.pushJobs = pushJobs == null ? new ArrayList<>() : pushJobs;
        }

        public static class PullJob {
            private String jobName = "pull-job";
            private boolean enabled;
            private String targetAet;
            private String destinationAet;
            private String patientId;
            private String modality;
            private int studyDateLookbackDays = 1;
            private int maxStudiesPerRun;
            private int maxInstancesPerRun;
            private long throttleDelayMs;

            public String getJobName() {
                return jobName;
            }

            public void setJobName(String jobName) {
                this.jobName = jobName;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getTargetAet() {
                return targetAet;
            }

            public void setTargetAet(String targetAet) {
                this.targetAet = targetAet;
            }

            public String getDestinationAet() {
                return destinationAet;
            }

            public void setDestinationAet(String destinationAet) {
                this.destinationAet = destinationAet;
            }

            public String getPatientId() {
                return patientId;
            }

            public void setPatientId(String patientId) {
                this.patientId = patientId;
            }

            public String getModality() {
                return modality;
            }

            public void setModality(String modality) {
                this.modality = modality;
            }

            public int getStudyDateLookbackDays() {
                return studyDateLookbackDays;
            }

            public void setStudyDateLookbackDays(int studyDateLookbackDays) {
                this.studyDateLookbackDays = studyDateLookbackDays;
            }

            public int getMaxStudiesPerRun() {
                return maxStudiesPerRun;
            }

            public void setMaxStudiesPerRun(int maxStudiesPerRun) {
                this.maxStudiesPerRun = maxStudiesPerRun;
            }

            public int getMaxInstancesPerRun() {
                return maxInstancesPerRun;
            }

            public void setMaxInstancesPerRun(int maxInstancesPerRun) {
                this.maxInstancesPerRun = maxInstancesPerRun;
            }

            public long getThrottleDelayMs() {
                return throttleDelayMs;
            }

            public void setThrottleDelayMs(long throttleDelayMs) {
                this.throttleDelayMs = throttleDelayMs;
            }
        }

        public static class PushJob {
            private String jobName = "push-job";
            private boolean enabled;
            private String targetAet;
            private String patientId;
            private String modality;
            private int studyDateLookbackDays = 1;
            private boolean preventLoopToSource = true;
            private boolean skipRemoteDuplicates = true;
            private int maxStudiesPerRun;
            private int maxInstancesPerRun;
            private long throttleDelayMs;
            private List<String> sourceAetAllowList = new ArrayList<>();
            private List<String> sourceAetBlockList = new ArrayList<>();

            public String getJobName() {
                return jobName;
            }

            public void setJobName(String jobName) {
                this.jobName = jobName;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getTargetAet() {
                return targetAet;
            }

            public void setTargetAet(String targetAet) {
                this.targetAet = targetAet;
            }

            public String getPatientId() {
                return patientId;
            }

            public void setPatientId(String patientId) {
                this.patientId = patientId;
            }

            public String getModality() {
                return modality;
            }

            public void setModality(String modality) {
                this.modality = modality;
            }

            public int getStudyDateLookbackDays() {
                return studyDateLookbackDays;
            }

            public void setStudyDateLookbackDays(int studyDateLookbackDays) {
                this.studyDateLookbackDays = studyDateLookbackDays;
            }

            public boolean isPreventLoopToSource() {
                return preventLoopToSource;
            }

            public void setPreventLoopToSource(boolean preventLoopToSource) {
                this.preventLoopToSource = preventLoopToSource;
            }

            public boolean isSkipRemoteDuplicates() {
                return skipRemoteDuplicates;
            }

            public void setSkipRemoteDuplicates(boolean skipRemoteDuplicates) {
                this.skipRemoteDuplicates = skipRemoteDuplicates;
            }

            public int getMaxStudiesPerRun() {
                return maxStudiesPerRun;
            }

            public void setMaxStudiesPerRun(int maxStudiesPerRun) {
                this.maxStudiesPerRun = maxStudiesPerRun;
            }

            public int getMaxInstancesPerRun() {
                return maxInstancesPerRun;
            }

            public void setMaxInstancesPerRun(int maxInstancesPerRun) {
                this.maxInstancesPerRun = maxInstancesPerRun;
            }

            public long getThrottleDelayMs() {
                return throttleDelayMs;
            }

            public void setThrottleDelayMs(long throttleDelayMs) {
                this.throttleDelayMs = throttleDelayMs;
            }

            public List<String> getSourceAetAllowList() {
                return sourceAetAllowList;
            }

            public void setSourceAetAllowList(List<String> sourceAetAllowList) {
                this.sourceAetAllowList = sourceAetAllowList == null ? new ArrayList<>() : sourceAetAllowList;
            }

            public List<String> getSourceAetBlockList() {
                return sourceAetBlockList;
            }

            public void setSourceAetBlockList(List<String> sourceAetBlockList) {
                this.sourceAetBlockList = sourceAetBlockList == null ? new ArrayList<>() : sourceAetBlockList;
            }
        }
    }

    public static class S3 {
        private String endpoint = "http://localhost:9000";
        private String bucket = "pacs-storage";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String region = "us-east-1";
        private boolean pathStyleAccess = true;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public boolean isPathStyleAccess() {
            return pathStyleAccess;
        }

        public void setPathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
        }
    }

    public static class FastDfs {
        private String trackerServer = "localhost:22122";
        private String groupName = "group1";
        private int connectTimeoutMs = 5000;
        private int networkTimeoutMs = 30000;

        public String getTrackerServer() {
            return trackerServer;
        }

        public void setTrackerServer(String trackerServer) {
            this.trackerServer = trackerServer;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getNetworkTimeoutMs() {
            return networkTimeoutMs;
        }

        public void setNetworkTimeoutMs(int networkTimeoutMs) {
            this.networkTimeoutMs = networkTimeoutMs;
        }
    }
}
