# StreetSafe

StreetSafe is a community safety mobile application designed to help users report road and neighborhood incidents, view reported hazards on a map, and identify safer routes based on active incident data.

The system combines an Android mobile application for community users with a web-based administrative portal for reviewing, validating, and managing submitted incident reports.

This project was developed as an academic project and demonstrates mobile application development, Firebase integration, geolocation, map-based visualization, cloud image handling, external API integration, and administrative report management.

---

## Overview

StreetSafe aims to provide users with location-based awareness of incidents that may affect their safety while traveling.

Users can submit incident reports containing information such as the incident category, description, location, and supporting images. Submitted reports are stored in Firebase and reviewed by an administrator before they become active within the system.

Approved incidents can then be displayed on the map and considered when users search for safer routes.

The system consists of two main components:

* **StreetSafe Android Application** – used by community members to report and view incidents and search for safer routes.
* **StreetSafe Admin Web Portal** – used by administrators to review, approve, reject, manage, and resolve submitted reports.

---

## Main Features

### User Authentication

StreetSafe provides account-based access for mobile users.

Users can:

* Register an account
* Sign in using their credentials
* Access protected application features
* Manage their user information
* View their submitted incident reports

Firebase Authentication is used to handle user authentication.

---

## Incident Reporting

Users can submit safety-related incident reports through the Android application.

A report may contain:

* Incident category
* Incident description
* Geographic location
* Supporting image
* Date and time of submission
* Report status

The user's location can be associated with the incident so that the report can later be displayed geographically within the application.

Images associated with reports can be uploaded using cloud image storage.

---

## Incident Report Status

Submitted incident reports follow a moderation workflow before becoming publicly visible within the system.

```text
User submits report
        │
        ▼
     PENDING
      /    \
     /      \
 APPROVED   REJECTED
     │
     ▼
   ACTIVE
     │
     ▼
  RESOLVED
```

### Pending

A newly submitted report initially enters the `PENDING` state.

The report waits for administrator review.

### Active

When an administrator approves a report, its status becomes `ACTIVE`.

Active incidents can be displayed on the map and used by the routing functionality when analyzing potential safety concerns.

### Rejected

If an administrator determines that a report should not be accepted, the report can be marked as `REJECTED`.

Rejected reports are not treated as active hazards.

### Resolved

An active report may later be marked as `RESOLVED` once the incident is no longer considered active.

---

## Incident Map

StreetSafe provides a map-based interface that allows users to view reported incidents based on their geographic locations.

The map helps users understand where safety-related incidents have been reported within an area.

Incident information may include:

* Incident location
* Incident category
* Risk classification
* Report status
* Incident details

Only relevant active incidents are intended to affect the user's current safety information.

---

## Incident Risk Classification

StreetSafe categorizes incidents according to their potential risk level.

The system supports classifications such as:

* **High Risk**
* **Medium Risk**
* **Low Risk**

These classifications help communicate the relative severity of reported incidents to users viewing the map or evaluating a route.

---

## Safer Route Feature

StreetSafe includes a route-planning feature intended to help users evaluate routes while considering nearby active incidents.

The application uses routing and geographic information to determine a possible route between a starting point and a destination.

The generated route can then be evaluated against known active incident locations.

The safer-route functionality uses:

* User location
* Destination information
* Geographic coordinates
* Active incident locations
* Routing information
* Proximity between incidents and the proposed route

OpenRouteService is used for routing functionality.

This feature is intended to provide additional situational awareness and should not be considered a guarantee that a route is completely safe.

---

## Location Search

StreetSafe supports location searching when selecting destinations.

Location information can be converted into geographic coordinates that can then be used by the route-planning functionality.

The application makes use of map and geographic services to support:

* Location searching
* Coordinate retrieval
* Route generation
* Incident visualization

---

## User Reports

Users can view information related to the reports they have previously submitted.

This allows users to track their reporting activity and view the current status of their reports.

Possible statuses include:

```text
Pending
Active
Rejected
Resolved
```

---

## User Profile

StreetSafe includes profile-related functionality where users can access information associated with their account.

The profile section provides a centralized place for users to manage account-related information and access their activity within the system.

---

# Admin Web Portal

StreetSafe includes a separate administrative web interface for managing community incident reports.

Administrators are responsible for reviewing reports before they become active within the system.

---

## Administrator Dashboard

The administrative portal provides administrators with an overview of reports submitted through the StreetSafe mobile application.

Administrators can monitor reports and perform moderation actions.

---

## Report Review

Administrators can review submitted incident information before approving it.

Report information may include:

* Incident type
* Description
* Location
* Supporting image
* Submission information
* Current status

This moderation process helps prevent every submitted report from automatically appearing as an active incident.

---

## Approve Reports

Administrators may approve valid incident reports.

Once approved, a report can become an active incident and may appear in the application's safety-related map information.

---

## Reject Reports

Administrators may reject reports that should not become active incidents.

Rejected reports remain excluded from the active incident dataset.

---

## Resolve Incidents

Previously approved incidents may later be marked as resolved.

This allows StreetSafe to distinguish between currently active safety concerns and incidents that are no longer considered active.

---

## Real-Time Data

StreetSafe uses Firebase services for storing and synchronizing application data.

Changes to reports can be reflected between the mobile application and the administrative portal through the shared cloud database.

This allows report information and status changes to remain synchronized between different components of the system.

---

# Technology Stack

## Mobile Application

* **Kotlin**
* **Android Studio**
* **Android SDK**
* **Firebase Authentication**
* **Cloud Firestore**
* **Google Maps**
* **OpenRouteService**
* **Cloudinary**
* **OpenStreetMap / geographic search services**

## Cloud Services

* **Firebase Authentication** – user authentication
* **Cloud Firestore** – storage of users and incident reports
* **Cloudinary** – image hosting for report-related images

## Mapping and Location

* **Google Maps** – map visualization
* **OpenRouteService** – route generation
* Geographic coordinates and location-based incident analysis

## Administration

* Web-based administrative interface
* Firebase-connected report management
* Incident moderation workflow

---

# System Workflow

A simplified StreetSafe workflow is shown below.

```text
COMMUNITY USER
      │
      ▼
StreetSafe Android App
      │
      ├── Register / Login
      │
      ├── Submit Incident
      │       │
      │       ▼
      │   Cloud Firestore
      │       │
      │       ▼
      │   Pending Report
      │
      ▼
ADMIN WEB PORTAL
      │
      ├── Review Report
      │
      ├── Approve
      │
      ├── Reject
      │
      └── Resolve
              │
              ▼
        Updated Firestore Data
              │
              ▼
      StreetSafe Android App
              │
              ├── Incident Map
              ├── Active Reports
              └── Safer Route Analysis
```

---

# Project Structure

The project contains both the Android application and the administrative web interface.

```text
StreetSafe/
│
├── app/
│   └── Android application source code
│
├── StreetSafe-Website/
│   └── Administrative web interface
│
├── gradle/
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── local.properties
└── README.md
```

---

# Local Development Setup

## Requirements

To run the Android application locally, you will need:

* Android Studio
* Android SDK
* JDK compatible with the project
* Firebase project
* Google Maps API access
* OpenRouteService API access
* Cloudinary configuration
* Internet connection for cloud-based services

---

## Clone the Repository

```bash
git clone https://github.com/harriz03/StreetSafe.git
```

Then open the project using Android Studio.

---

## Local Configuration

API keys and private environment-specific settings should not be permanently hard-coded into source files.

A local configuration may contain values similar to:

```properties
MAPS_API_KEY=your_google_maps_api_key
ORS_API_KEY=your_openrouteservice_api_key
CLOUDINARY_CLOUD_NAME=your_cloudinary_cloud_name
CLOUDINARY_UPLOAD_PRESET=your_upload_preset
```

Replace the placeholder values with your own development credentials.

Do not commit private credentials or unrestricted API keys to a public repository.

---

# Firebase Configuration

StreetSafe relies on Firebase for authentication and cloud data storage.

To run your own instance of the project, create a Firebase project and configure:

* Firebase Authentication
* Cloud Firestore
* Android application registration
* Appropriate Firestore security rules

The Android Firebase configuration file should be added locally according to the Firebase setup instructions.

---

# Security Notes

This repository is intended to demonstrate the implementation of an academic software project.

Production applications should follow additional security practices, including:

* Restricting API keys
* Keeping private credentials outside source control
* Applying appropriate Firebase security rules
* Validating user-generated content
* Protecting administrative functionality
* Implementing appropriate access control
* Rotating compromised or previously exposed API keys
* Monitoring API usage and access logs

---

# Limitations

StreetSafe is an academic prototype and should not be treated as an official emergency response, law-enforcement, or navigation service.

Incident information is based on reports submitted through the system and may not represent every real-world safety condition.

The safer-route feature is intended to provide additional situational awareness rather than guarantee user safety.

Users should continue to follow official safety guidance and emergency procedures when necessary.

---

# Purpose of the Project

StreetSafe was developed to demonstrate the integration of:

* Android mobile development
* Cloud-based authentication
* Real-time database functionality
* Geographic information
* Map visualization
* External APIs
* Image hosting
* User-generated incident reporting
* Administrative moderation
* Route-based safety analysis

The project also provided practical experience working with multiple services and connecting a mobile application with a separate administrative web interface through shared cloud data.

---

# Academic Project

StreetSafe was developed as an academic software project.

It is intended for educational and portfolio purposes and demonstrates the application of mobile development, cloud services, mapping technologies, APIs, and administrative system design.

 
