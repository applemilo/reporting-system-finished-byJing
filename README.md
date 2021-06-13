### What I did

* **add S3 configuration**

```java
@Configuration
public class S3Config {
    @Bean
    public static AmazonS3Client amazonS3Client() {
        return (AmazonS3Client) AmazonS3ClientBuilder.standard().withCredentials(new DefaultAWSCredentialsProviderChain()).build();
    }
}
```



* **add new feature delete**

```java
//controller layer

@DeleteMapping
    @DeleteMapping("/report/content/{reqId}/{type}")
    public void deleteFile(@PathVariable String reqId, @PathVariable FileType type, HttpServletResponse response) throws IOException {
    log.debug("Got Request to Delete FIle - type: {}, reqid: {}", type,reqId);
    reportService.deleteFileByReqId(reqId, type);
    log.debug("File:{} deleted", reqId);
        
// service layer interface + imp
      void deleteFileByReqId(String reqId, FileType type);
        
         @Override
    public void deleteFileByReqId(String reqId, FileType type) {

        ReportRequestEntity entity = reportRequestRepo.findById(reqId).orElseThrow(RequestNotFoundException::new);
        if (type == FileType.PDF) {
            String fileLocation = entity.getPdfReport().getFileLocation();
            String bucket = fileLocation.split("/")[0];
            String key = fileLocation.split("/")[1];

            s3Client.deleteObject(bucket, key);

        } else if (type == FileType.EXCEL) {
            String fileId = entity.getExcelReport().getFileId();
            reportRequestRepo.deleteById(fileId);

        }

    }
        
```

* **Improve sync API performance by using multithreading and sending request concurrently to both services**

```java
// first step to configure the threadpool
@Configuration
@EnableAsync
public class AsyncConfig extends AsyncConfigurerSupport {

    @Autowired
    BeanFactory beanFactory;

    @Bean
    @Primary
    public ThreadPoolTaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(10);
        threadPoolTaskExecutor.setMaxPoolSize(50);
        threadPoolTaskExecutor.initialize();

        return new LazyTraceThreadPoolTaskExecutor(beanFactory, threadPoolTaskExecutor);
    }
    
// use CompleteFuture to set multiplethread task
    private void sendDirectRequests(ReportRequest request) {

        ExcelResponse excelResponse = new ExcelResponse();
        PDFResponse pdfResponse = new PDFResponse();
        //es from config, rs could apply load balance
        CompletableFuture<ExcelResponse> excelFuture = CompletableFuture.supplyAsync(() -> rs.postForEntity
                ("http://localhost:8888/excel", request, ExcelResponse.class).getBody(), es);
        CompletableFuture<PDFResponse> pdfFuture = CompletableFuture.supplyAsync(() ->
                rs.postForEntity("http://localhost:9999/pdf", request, PDFResponse.class).getBody(), es);
        try {
            excelResponse = excelFuture.get();
        } catch (Exception e) {
            log.error("Excel Generation Error (Sync) : e", e);
            excelResponse.setReqId(request.getReqId());
            excelResponse.setFailed(true);
        } finally {
            updateLocal(excelResponse);
        }
        try {
            pdfResponse = pdfFuture.get();
        } catch (Exception e) {
            log.error("PDF Generation Error (Sync) : e", e);
            pdfResponse.setReqId(request.getReqId());
            pdfResponse.setFailed(true);
        } finally {
            updateLocal(pdfResponse);
        }
    }
```



* **Use a database instead of hashmap in the ExcelRepositoryImpl.** 

```java
//set mongoDB
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=test
spring.data.mongodb.username=root
spring.data.mongodb.password=mypassword

// use spring data jpa(mongoDB) to replace hashmap 
public interface ExcelRepository extends MongoRepository<ExcelFile, String> {
    Optional<ExcelFile> getFileById(String id);

    ExcelFile saveFile(ExcelFile file);

    ExcelFile deleteFile(String id);

    List<ExcelFile> getFiles();
}
// test part of part of apis

public class RepoTest {
    @Autowired
    ExcelRepository excelRepository;
    Logger log = LoggerFactory.getLogger("Test");

    @Test
    public void testSearch() {
        Optional<ExcelFile> res = excelRepository.getFileById("1");
        System.out.println(res);
    }

    @Test
    public void testGetFile(){
        List<ExcelFile> res = excelRepository.getFiles();
        log.debug("ExcelFile Generated Size:"+res.size());

    }
}
```



* **Convert sync API into microservices by adding Eureka/Ribbon support.**

```java
//first add a Eureka Server(I just set one eureka server. It also can more than one and register with each other)

server.port=8761
spring.application.name=EurekaServer


## Eureka Server Register
eureka.instance.hostname=localhost
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
eureka.client.service-url.defaultZone=http://${eureka.instance.hostname}:${server.port}/eureka/

//finish server setting
@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }

}

//register service provider and consumer to eureka server 
@SpringBootApplication
@EnableEurekaClient

public class MainClientApplication {
    @Bean
    public QueueMessagingTemplate queueMessagingTemplate(
            AmazonSQSAsync amazonSQSAsync) {
        return new QueueMessagingTemplate(amazonSQSAsync);
    }

    @Bean
    @LoadBalanced
    public RestTemplate getRestTemplate() {
        return new RestTemplate();
    }
    public static void main(String[] args) {
        SpringApplication.run(MainClientApplication.class, args);
    }

}

    


```

