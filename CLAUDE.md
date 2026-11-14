# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Health & Habit Tracker - A comprehensive Android application for health monitoring, habit tracking, and AI-powered nutrition analysis.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run lint checks
./gradlew lint

# Run connected Android tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Build specific module
./gradlew :app:assembleDebug
```

## Architecture

This project follows **Clean Architecture** with **MVVM** pattern:

```
app/
├── data/           # Data layer (repositories, data sources, Room database)
├── domain/         # Business logic (use cases, entities, repository interfaces)
├── presentation/   # UI layer (ViewModels, Compose screens, navigation)
├── di/             # Dependency injection (Hilt modules)
└── core/           # Shared utilities, constants, extensions
```

### Dependency Flow
Presentation → Domain → Data (dependencies flow inward, imports flow outward)

### Key Layers
- **domain/**: Pure Kotlin with business logic, no Android dependencies
- **data/**: Room entities, DAOs, repository implementations
- **presentation/**: Jetpack Compose UI, ViewModels, UI state
- **di/**: Hilt dependency injection modules

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM + Clean Architecture
- **Database**: Room (SQLite)
- **Preferences**: DataStore
- **DI**: Hilt
- **Navigation**: Compose Navigation
- **Charts**: MPAndroidChart or Vico
- **Background**: WorkManager
- **Camera**: CameraX
- **Health**: Health Connect

## Database Schema

Main tables (Room entities):
- `Account` - User authentication
- `UserProfile` - Personal health information
- `Habit` / `HabitLog` - Habit tracking
- `Food` / `Meal` / `MealItem` - Nutrition data
- `WaterLog` - Water intake
- `SleepLog` - Sleep tracking
- `ActivityLog` - Activity/exercise tracking
- `WeightLog` - Weight history
- `Reminder` - Notifications
- `AIAnalysis` - AI analysis results

## Navigation Structure

Bottom nav with 5 tabs:
1. Home (Dashboard)
2. Nutrition
3. Habits
4. Reports
5. Profile

Key destinations: Login, Registration, Dashboard, AI Food Scanner, Food Analysis Result, Meal Details, Habit List, Water/Sleep/Activity Tracking, Health Profile, AI Assistant, Settings.

## API Keys & Secrets

All API keys must be stored in `gradle.properties` or `local.properties`:
```properties
AI_VISION_API_KEY=your_key_here
FIREBASE_API_KEY=your_key_here
```

Never hardcode credentials. Use Firebase Remote Config or a secure backend for AI API calls.

## Development Phases

1. Project setup, architecture, database
2. Authentication, profile, dashboard, habits, water tracking
3. Sleep, activity, nutrition tracking, meal history
4. AI food recognition, AI Health Assistant
5. Reports, charts, notifications, Health Connect
6. Testing, bug fixes, release build

## Testing Strategy

- Unit tests for domain layer (use cases, repositories)
- ViewModel tests for presentation logic
- Instrumented tests for Room database operations
- UI tests with Compose Testing

## Notes

- Use `kotlinx.coroutines` for async operations
- All database operations must be non-blocking
- Follow Material 3 design guidelines
- Primary color: Green (emerald), Background: White/Light Gray — plus a bright accent per health area (see `HealthPalette`: Coral=heart, Pink=mind/BP, Orange=activity/food, Sky=water, Violet=sleep/habits, Indigo=meds/reports, Amber=reminders). Don't use green alone.
- Build screens from `presentation/ui/components` (`AppCard`, `GradientCard`, `IconBadge`, `ScreenHeader`, `StatTile`, `PrimaryButton`, `SoftButton`, `BackTopBar`) rather than raw Cards/colors
- Mock AI responses during development, label clearly