<h1 align="center">
  <br>
  SoundGuard
  <br>
</h1>

<h4 align="center">Your intelligent environmental awareness companion for Pixel Watch and Android.</h4>

<p align="center">
  <a href="#about-the-project">About The Project</a> •
  <a href="#how-it-works">How It Works</a> •
  <a href="#key-features">Key Features</a> •
  <a href="#technologies-used">Technologies Used</a> •
  <a href="#project-structure">Project Structure</a>
</p>

---

## 🎧 About The Project

**SoundGuard** is an innovative safety application designed for individuals who wear headphones in public, as well as for people with hearing impairments. The system monitors the surrounding environment for critical sounds (e.g., sirens, alarms, horns) and alerts the user through their smartwatch, ensuring they remain aware of potential hazards even when they cannot hear them.

## ⚙️ How It Works

1. **Listen:** The Pixel Watch 4 continuously monitors the environment for audio signals and transmits the data to the connected Android phone.
2. **Analyze:** The smartphone processes the incoming audio data to identify specific sound signatures and assess their severity.
3. **Alert:** Upon detecting a critical sound, the phone triggers a tailored alert on the smartwatch. Depending on the severity, the watch will deliver strong haptic feedback (vibrations) and display a full-screen warning.
4. **Emergency SOS:** If a high-severity alert is ignored or not dismissed by the user, the app automatically triggers an SOS protocol, sending the user's live location to designated emergency contacts.

## ✨ Key Features

- **Real-Time Sound Recognition:** Instant identification of environmental hazards.
- **Smart Haptic Alerts:** Tailored vibration patterns on the Pixel Watch based on the severity of the detected event.
- **Automated SOS System:** Automatic emergency outreach if the user is unresponsive to critical alerts.
- **Personalized AI Coach:** Get personalized insights based on your alert history and environment.

## 🚀 Technologies Used

SoundGuard leverages a modern and robust tech stack to deliver reliable real-time alerts:

### Mobile & Wearable (Kotlin)
- **Android App:** Built natively for Android smartphones to handle heavy audio processing and API communications.
- **Wear OS App:** Optimized for Pixel Watch 4 to capture audio and deliver haptic/visual feedback efficiently.

### APIs & Integrations
- **🧠 Gemini API (`gemini-2.5-flash-lite`):** Powers the intelligent **Coach** feature. The AI acts as a personalized safety assistant, analyzing your alert history to provide tailored insights and briefings. It supports generating grounded responses based on user context and can speak its replies aloud.
- **🔐 Google OAuth (Sign-in with Google):** Provides a seamless and secure login experience for users. Crucially, it is used to obtain the necessary OAuth access tokens required by the Gmail REST API to send automated SOS emails directly from the user's account without needing separate email credentials.
- **🗺️ Google Maps API:** Enhances the Emergency SOS feature by appending accurate GPS coordinates and generating a direct, clickable Google Maps link (`https://maps.google.com/?q=lat,lng`), allowing emergency contacts to locate the user instantly in case of an incident.

### Backend (Node.js, JS, MongoDB)
- **Web App / API:** A centralized backend managing user data and preferences. Deployed seamlessly on [Railway](https://soundguard.up.railway.app/).
- **Database:** MongoDB for flexible, scalable data storage.

## 📁 Project Structure

```text
SoundGuard/
├── app/                  # Android Smartphone Application
├── wear/                 # Wear OS Application (Pixel Watch 4)
```

## 🛠️ Getting Started

### Prerequisites
- Android Studio.
- A physical Android device and a Pixel Watch 4 (or Wear OS emulator).
- API Keys for Google Maps, Gemini, and Google OAuth.

### Installation
1. Clone the repository.
2. Create a `local.properties` file in the root directory and add your API keys:
   ```properties
   GEMINI_API_KEY=your_gemini_api_key_here
   ```
3. Sync the project with Gradle.
4. Run the `app` module on your Android phone.
5. Run the `wear` module on your Pixel Watch 4.

---
*Built with ❤️ for safety and accessibility.*
