package org.example.infrastructure.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

import java.util.Optional;

@ApplicationScoped
public class ChatLambdaConfig {

    @ConfigProperty(name = "aws.dynamodb.region", defaultValue = "sa-east-1")
    String region;

    @ConfigProperty(name = "aws.access-key-id")
    Optional<String> accessKeyId;

    @ConfigProperty(name = "aws.secret-access-key")
    Optional<String> secretAccessKey;

    @Produces
    @ApplicationScoped
    public LambdaClient lambdaClient() {
        var builder = LambdaClient.builder().region(Region.of(region));

        if (accessKeyId.isPresent() && !accessKeyId.get().isBlank()
                && secretAccessKey.isPresent() && !secretAccessKey.get().isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKeyId.get(), secretAccessKey.get())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
