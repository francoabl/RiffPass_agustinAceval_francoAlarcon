package cl.riffpass.admin.config;

import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/** Clientes síncronos para la administración. Usan la misma región y credenciales que Spring Cloud AWS. */
@Configuration
public class AwsClientsConfig {
    @Bean
    SqsClient sqsClient(AwsCredentialsProvider credenciales, AwsRegionProvider region) {
        return SqsClient.builder().credentialsProvider(credenciales).region(region.getRegion()).build();
    }
    @Bean
    CloudWatchClient cloudWatchClient(AwsCredentialsProvider credenciales, AwsRegionProvider region) {
        return CloudWatchClient.builder().credentialsProvider(credenciales).region(region.getRegion()).build();
    }
}
