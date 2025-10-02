# ClubMS - School Club Management System

A comprehensive JavaFX desktop application designed to streamline student club management, enrollment, and attendance tracking for educational institutions.

## 🚀 Key Features

### 👥 User Management & Security
- **Role-based Access Control**: Coordinators, Teachers, and Administrators
- **Secure Authentication**: BCrypt password encryption with temporary password workflow
- **Password Management**: Secure reset functionality with time-limited temporary passwords

### 🎯 Club Management
- **Full Club Lifecycle**: Create, deactivate, and reactivate clubs with multiple teacher assignments
- **Teacher Coordination**: Assign multiple teachers to clubs with proper access controls
- **Student Enrollment**: Bulk import student data via Excel integration

### 📊 Analytics & Reporting
- **Real-time Dashboard**: Overview with high-level statistics (active clubs, teacher involvement, participation rates)
- **Attendance Tracking**: Mark student attendance (Present/Absent/Late) per session
- **Reporting System**: Generate detailed reports with visual analytics (foundation implemented)

### 🎨 User Experience
- **Intuitive JavaFX Interface**: Modern, user-friendly desktop application
- **Contextual Guidance**: Comprehensive tooltips throughout the application
- **Data Visualization**: Clear display of daily attendance and participation metrics

## 🛠️ Technology Stack

- **Backend**: Java 23.0.1
- **UI Framework**: JavaFX 21
- **Build Tool**: Maven
- **Security**: BCrypt password encryption
- **Data Import**: Excel file processing
- **IDE**: IntelliJ IDEA
- **Version Control**: Git & GitHub

## 📋 Prerequisites

- **Java JDK 23** or higher
- **JavaFX SDK 21** or compatible version
- **Maven 3.6+** (for dependency management)

## 🔧 Installation & Setup

### 1. Clone the Repository
```bash
git clone https://github.com/kevinnyangweso/clubms.git
cd clubms