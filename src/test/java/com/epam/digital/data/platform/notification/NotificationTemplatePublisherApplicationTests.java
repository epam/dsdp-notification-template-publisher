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
import com.epam.digital.data.platform.notification.service.EmailNotificationLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.ResourceUtils;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class NotificationTemplatePublisherApplicationTests {

  @Mock
  private ApplicationArguments args;

  @Mock
  private EmailNotificationLoader emailNotificationLoader;

  private String notificationDirectoryName;
  private AppProperties appProperties;
  private NotificationTemplatePublisherApplication notificationTemplatePublisherApplication;

  @Mock
  private NotificationTemplateRestClient templateRestClient;

  @BeforeEach
  void setup() throws FileNotFoundException {
    notificationDirectoryName = ResourceUtils.getFile("classpath:notifications").getAbsolutePath();
    appProperties = new AppProperties();
    appProperties.setNotificationsDirectoryName(notificationDirectoryName);
    appProperties.setChannelsAvailable(Set.of("email"));
    notificationTemplatePublisherApplication =
        new NotificationTemplatePublisherApplication(
            appProperties, Map.of("email", emailNotificationLoader), templateRestClient);
  }

  @Test
  void shouldCallServiceForEachFolder() {
    when(args.containsOption("notification_templates")).thenReturn(true);
    notificationTemplatePublisherApplication.run(args);

    verify(emailNotificationLoader, times(3)).loadDir(any());
  }

  @Test
  void shouldSkipChannelHandlingIfChannelLoaderNotExists() {
    notificationTemplatePublisherApplication =
            new NotificationTemplatePublisherApplication(
                    appProperties, Map.of(), templateRestClient);

    when(args.containsOption("notification_templates")).thenReturn(true);
    notificationTemplatePublisherApplication.run(args);

    verify(emailNotificationLoader, never()).loadDir(any());
  }

  @Test
  void shouldSkipChannelHandlingIfChannelNotAvailable() {
    appProperties = new AppProperties();
    appProperties.setNotificationsDirectoryName(notificationDirectoryName);
    appProperties.setChannelsAvailable(Set.of());
    notificationTemplatePublisherApplication =
            new NotificationTemplatePublisherApplication(
                    appProperties, Map.of("email", emailNotificationLoader), templateRestClient);

    when(args.containsOption("notification_templates")).thenReturn(true);
    notificationTemplatePublisherApplication.run(args);

    verify(emailNotificationLoader, never()).loadDir(any());
  }

  @Test
  void shouldReturnEmptyListOfFilesWhenNotificationFolderAbsent() {
    when(args.containsOption("notification_templates")).thenReturn(true);
    appProperties.setNotificationsDirectoryName(notificationDirectoryName + "a");
    notificationTemplatePublisherApplication.run(args);

    verify(emailNotificationLoader, never()).loadDir(any());
  }

  @Test
  void shouldCallDeleteTemplate() {
    when(args.containsOption("notification_templates")).thenReturn(true);
    UUID uuidNotExist1 = UUID.randomUUID();
    UUID uuidNotExist2 = UUID.randomUUID();
    UUID uuidExist = UUID.randomUUID();
    when(templateRestClient.getAllTemplates()).thenReturn(
            List.of(new NotificationTemplateShortInfoResponseDto(uuidNotExist1, "NotExistDiiaNotification", "diia"),
                    new NotificationTemplateShortInfoResponseDto(uuidNotExist2, "NotExistEmailNotification", "email"),
                    new NotificationTemplateShortInfoResponseDto(uuidExist, "SendDiiaNotificationWithMetadata", "diia"),
                    new NotificationTemplateShortInfoResponseDto(uuidExist, "SendInboxNotificationWithMetadata", "inbox"),
                    new NotificationTemplateShortInfoResponseDto(uuidExist, "RequestAppliedNotification", "email"),
                    new NotificationTemplateShortInfoResponseDto(uuidExist, "SendEmailNotification", "email"),
                    new NotificationTemplateShortInfoResponseDto(uuidExist, "SendEmailNotificationWithMetadata", "email")));
    notificationTemplatePublisherApplication.run(args);

    verify(templateRestClient, times(1)).deleteTemplate(uuidNotExist1);
    verify(templateRestClient, times(1)).deleteTemplate(uuidNotExist2);
    verify(templateRestClient, never()).deleteTemplate(uuidExist);
  }

  @Test
  void shouldDeleteAllAvailableChannelTemplatesOnCleanup() {
    when(args.containsOption("cleanup")).thenReturn(true);
    UUID emailId = UUID.randomUUID();
    UUID diiaId = UUID.randomUUID();
    UUID inboxId = UUID.randomUUID();
    when(templateRestClient.getAllTemplates()).thenReturn(
        List.of(
            new NotificationTemplateShortInfoResponseDto(emailId, "SomeEmailTemplate", "email"),
            new NotificationTemplateShortInfoResponseDto(diiaId, "SomeDiiaTemplate", "diia"),
            new NotificationTemplateShortInfoResponseDto(inboxId, "SomeInboxTemplate", "inbox")));

    notificationTemplatePublisherApplication.run(args);

    verify(templateRestClient, times(1)).deleteTemplate(emailId);
    verify(templateRestClient, never()).deleteTemplate(diiaId);
    verify(templateRestClient, never()).deleteTemplate(inboxId);
  }

  @Test
  void shouldSkipTemplatesForUnavailableChannelsOnCleanup() {
    appProperties.setChannelsAvailable(Set.of("email", "inbox"));
    notificationTemplatePublisherApplication =
        new NotificationTemplatePublisherApplication(
            appProperties, Map.of("email", emailNotificationLoader), templateRestClient);

    when(args.containsOption("cleanup")).thenReturn(true);
    UUID emailId = UUID.randomUUID();
    UUID diiaId = UUID.randomUUID();
    UUID inboxId = UUID.randomUUID();
    when(templateRestClient.getAllTemplates()).thenReturn(
        List.of(
            new NotificationTemplateShortInfoResponseDto(emailId, "SomeEmailTemplate", "email"),
            new NotificationTemplateShortInfoResponseDto(diiaId, "SomeDiiaTemplate", "diia"),
            new NotificationTemplateShortInfoResponseDto(inboxId, "SomeInboxTemplate", "inbox")));

    notificationTemplatePublisherApplication.run(args);

    verify(templateRestClient, times(1)).deleteTemplate(emailId);
    verify(templateRestClient, never()).deleteTemplate(diiaId);
    verify(templateRestClient, times(1)).deleteTemplate(inboxId);
  }

  @Test
  void shouldNotRunCleanupWhenFlagAbsent() {
    when(args.containsOption("cleanup")).thenReturn(false);
    when(args.containsOption("notification_templates")).thenReturn(false);

    notificationTemplatePublisherApplication.run(args);

    verify(templateRestClient, never()).getAllTemplates();
    verify(templateRestClient, never()).deleteTemplate(any());
  }

  @Test
  void shouldHandleEmptyTemplateListOnCleanup() {
    when(args.containsOption("cleanup")).thenReturn(true);
    when(templateRestClient.getAllTemplates()).thenReturn(List.of());

    notificationTemplatePublisherApplication.run(args);

    verify(templateRestClient, never()).deleteTemplate(any());
  }
}
