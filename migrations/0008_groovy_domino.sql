ALTER TABLE `user_profile` ADD `weather_enabled` integer DEFAULT false NOT NULL;--> statement-breakpoint
-- Installs that already use weather (a home place or a day elsewhere) keep it on; a fresh install starts with it off.
UPDATE `user_profile` SET `weather_enabled` = 1 WHERE `location_lat` IS NOT NULL OR EXISTS (SELECT 1 FROM `entries` WHERE `entries`.`location_lat` IS NOT NULL);