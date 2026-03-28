/*
 * Copyright 2023 EPAM Systems.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.digital.data.platform.notification;

import com.epam.digital.data.platform.notification.client.NotificationTemplateRestClient;
import com.epam.digital.data.platform.notification.dto.client.NotificationTemplateShortInfoResponseDto;
import com.epam.digital.data.platform.notification.properties.AppProperties;
import com.epam.digital.data.platform.notification.service.NotificationDirectoryLoader;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@SpringBootApplication
public class NotificationTemplatePublisherApplication implements ApplicationRunner {

  private final Logger log = LoggerFactory.getLogger(NotificationTemplatePublisherApplication.class);

  private final AppProperties appProperties;

  private final Map<String, NotificationDirectoryLoader> templateDirLoaders;

  private final NotificationTemplateRestClient templateRestClient;

  public NotificationTemplatePublisherApplication(
      AppProperties appProperties,
      @Qualifier("templateDirLoaders")
          Map<String, NotificationDirectoryLoader> templateDirLoaders,
          NotificationTemplateRestClient templateRestClient) {
    this.appProperties = appProperties;
    this.templateDirLoaders = new HashMap<>(Objects.requireNonNullElse(templateDirLoaders, Collections.emptyMap()));
    this.templateRestClient = templateRestClient;
  }

  public static void main(String[] args) {
    SpringApplication.run(NotificationTemplatePublisherApplication.class, args);
  }


  @Override
  public void run(ApplicationArguments args) {
    if (args.containsOption("notification_templates")) {
      handleNotifications();
    }
    if (args.containsOption("cleanup")) {
      handleCleanup();
    }
  }

  private void handleCleanup() {
    log.info("Starting cleanup of notification templates for channels: {}",
        appProperties.getChannelsAvailable());
    var allTemplates = templateRestClient.getAllTemplates();
    allTemplates.stream()
        .filter(t -> appProperties.getChannelsAvailable().contains(t.getChannel()))
        .forEach(t -> {
          log.info("Deleting notification template '{}' from channel '{}'",
              t.getName(), t.getChannel());
          templateRestClient.deleteTemplate(t.getId());
        });
    log.info("Cleanup completed");
  }

  private void handleNotifications() {
    var channelDirs = getChannelDirectories();
    var allDbNotificationTemplates = templateRestClient.getAllTemplates();
    for (File channelDir: channelDirs) {
      log.info("Processing of directory {}", channelDir);
      if (!channelDir.isDirectory()) {
        continue;
      }
      var channelName = channelDir.toPath().getFileName().toString();
      processChannelTemplates(channelName, channelDir);

      var channelNotificationTemplates = allDbNotificationTemplates.stream().filter(
              template -> template.getChannel().equals(channelName)).collect(Collectors.toList());
      deleteNonExistingChannelTemplates(channelDir, channelNotificationTemplates);
    }
  }

  private List<File> getChannelDirectories() {
    var rootDir = FileUtils.getFile(appProperties.getNotificationsDirectoryName());
    return Optional.ofNullable(rootDir.listFiles())
        .map(Arrays::asList)
        .orElseGet(
            () -> {
              log.error("Directory {} does not exist", rootDir);
              return Collections.emptyList();
            });
  }

  private void processChannelTemplates(String channelName, File channelDir) {
    var channelTemplateLoader = templateDirLoaders.get(channelName);
    if (channelTemplateLoader == null) {
      log.warn("No template loader for channel {}", channelName);
      return;
    }
    if (!appProperties.getChannelsAvailable().contains(channelName)) {
      log.warn("Channel {} is skipped because it is not available", channelName);
      return;
    }
    var templateDirectories = getTemplateDirectories(channelDir);
    for (File templateDir : templateDirectories) {
      channelTemplateLoader.loadDir(templateDir);
    }
  }

  private void deleteNonExistingChannelTemplates(File channelDir,
          List<NotificationTemplateShortInfoResponseDto> allDbNotificationTemplates) {
    var templateDirectories = getTemplateDirectories(channelDir);
    for (NotificationTemplateShortInfoResponseDto notificationTemplate : allDbNotificationTemplates) {
      if (!directoriesContainsName(templateDirectories, notificationTemplate.getName())) {
        log.info("Deleting notification template {}", notificationTemplate.getName());
        templateRestClient.deleteTemplate(notificationTemplate.getId());
      }
    }
  }

  private List<File> getTemplateDirectories(File channelDir) {
    return Arrays.stream(Optional.ofNullable(channelDir.listFiles())
                    .orElse(new File[]{}))
            .filter(File::isDirectory)
            .collect(Collectors.toList());
  }

  private boolean directoriesContainsName(List<File> templateDirectories, String name) {
    return templateDirectories.stream().anyMatch(dir -> dir.getName().equals(name));
  }
}
