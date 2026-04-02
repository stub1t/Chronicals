CREATE TABLE `entries` (
	`id` text PRIMARY KEY NOT NULL,
	`date` text NOT NULL,
	`note` text,
	`created_at` text DEFAULT (datetime('now')) NOT NULL,
	`updated_at` text DEFAULT (datetime('now')) NOT NULL
);
--> statement-breakpoint
CREATE INDEX `entries_date_idx` ON `entries` (`date`);--> statement-breakpoint
CREATE TABLE `entry_triggers` (
	`id` text PRIMARY KEY NOT NULL,
	`entry_id` text NOT NULL,
	`trigger_id` text NOT NULL,
	`note` text,
	FOREIGN KEY (`entry_id`) REFERENCES `entries`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`trigger_id`) REFERENCES `triggers`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `et_entry_idx` ON `entry_triggers` (`entry_id`);--> statement-breakpoint
CREATE INDEX `et_trigger_idx` ON `entry_triggers` (`trigger_id`);--> statement-breakpoint
CREATE UNIQUE INDEX `et_unique_pair` ON `entry_triggers` (`entry_id`,`trigger_id`);--> statement-breakpoint
CREATE TABLE `entry_type_logs` (
	`id` text PRIMARY KEY NOT NULL,
	`entry_id` text NOT NULL,
	`entry_type_id` text NOT NULL,
	`start_time` text NOT NULL,
	`end_time` text,
	`metric_values` text DEFAULT '{}' NOT NULL,
	`intensity_cache` real,
	`created_at` text DEFAULT (datetime('now')) NOT NULL,
	FOREIGN KEY (`entry_id`) REFERENCES `entries`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`entry_type_id`) REFERENCES `entry_types`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `etl_entry_idx` ON `entry_type_logs` (`entry_id`);--> statement-breakpoint
CREATE INDEX `etl_type_idx` ON `entry_type_logs` (`entry_type_id`);--> statement-breakpoint
CREATE INDEX `etl_start_idx` ON `entry_type_logs` (`start_time`);--> statement-breakpoint
CREATE TABLE `entry_types` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`icon` text,
	`color` text,
	`metric_schema` text NOT NULL,
	`category` text DEFAULT 'symptom' NOT NULL,
	`sort_order` integer DEFAULT 0 NOT NULL,
	`is_built_in` integer DEFAULT false NOT NULL,
	`created_at` text DEFAULT (datetime('now')) NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `entry_types_name_unique` ON `entry_types` (`name`);--> statement-breakpoint
CREATE TABLE `environmental_data` (
	`id` text PRIMARY KEY NOT NULL,
	`date` text NOT NULL,
	`temperature_c` real,
	`air_pressure_hpa` real,
	`pressure_delta_hpa` real,
	`weather_condition` text,
	`pollen_grass` real,
	`pollen_birch` real,
	`pollen_alder` real,
	`pollen_mugwort` real,
	`uv_index` real,
	`fetched_at` text DEFAULT (datetime('now')) NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `environmental_data_date_unique` ON `environmental_data` (`date`);--> statement-breakpoint
CREATE INDEX `env_date_idx` ON `environmental_data` (`date`);--> statement-breakpoint
CREATE TABLE `medication_changes` (
	`id` text PRIMARY KEY NOT NULL,
	`medication_id` text,
	`change_type` text NOT NULL,
	`old_dosage` text,
	`new_dosage` text,
	`change_date` text NOT NULL,
	`reason` text,
	`notes` text,
	`created_at` text DEFAULT (datetime('now')) NOT NULL,
	FOREIGN KEY (`medication_id`) REFERENCES `medications`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE INDEX `mc_date_idx` ON `medication_changes` (`change_date`);--> statement-breakpoint
CREATE TABLE `medications` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`dosage` text,
	`unit` text,
	`start_date` text NOT NULL,
	`end_date` text,
	`notes` text,
	`is_active` integer DEFAULT true NOT NULL,
	`created_at` text DEFAULT (datetime('now')) NOT NULL
);
--> statement-breakpoint
CREATE TABLE `triggers` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`category` text NOT NULL,
	`is_built_in` integer DEFAULT false NOT NULL,
	`created_at` text DEFAULT (datetime('now')) NOT NULL
);
--> statement-breakpoint
CREATE INDEX `triggers_category_idx` ON `triggers` (`category`);--> statement-breakpoint
CREATE UNIQUE INDEX `triggers_name_unique` ON `triggers` (`name`);--> statement-breakpoint
CREATE TABLE `user_profile` (
	`id` text PRIMARY KEY DEFAULT 'default' NOT NULL,
	`display_name` text,
	`blood_type` text,
	`location_lat` real,
	`location_lon` real,
	`location_name` text,
	`updated_at` text DEFAULT (datetime('now')) NOT NULL
);
