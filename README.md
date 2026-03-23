# 🧊 FridgeCheck

**An AI-Powered Smart Assistant for your Kitchen.**

FridgeCheck turns your smartphone into a culinary consultant. By combining Google’s Gemini 3 Flash for computer vision and the Edamam API for recipe intelligence, the app identifies ingredients in your fridge and suggests the best meals you can cook with what you already have.

## ✨ Features
**Visual Ingredient Scanning:** Snap a photo of your fridge, and Gemini AI identifies the items automatically.

**Smart Recipe Discovery:** Fetches real-time recipes via the Edamam Recipe Search API.

**Intelligent Sorting:** Recipes are prioritized by "Missing Ingredients," putting the meals you can cook right now at the top.

**Dynamic UI:** High-performance scrolling and image loading optimized for high-refresh-rate displays.

**Direct Access:** Tap any recipe card to open full cooking instructions in your mobile browser.

## 🛠️ Tech Stack
**Language:** Kotlin

**UI Framework:** Jetpack Compose (Material 3)

**AI Backend:** Google Generative AI SDK (Gemini 3 Flash)

**Networking:** Retrofit 2 & OkHttp

**Image Loading:** Coil (Coroutines Image Loader)

**Concurrency:** Kotlin Coroutines

**Hardware Integration:** CameraX API

**Dependency Management:** Gradle Version Catalog (libs.versions.toml)

## 🚀 Getting Started
### Prerequisites
**Gemini API Key:** Get one from the Google AI Studio.

**Edamam API Credentials:** Register for a Developer plan at Edamam. Minimum service is enough if you wish to use at home. 

### Installation
Clone the repository:

```Bash 
git clone https://github.com/daiyichen27/fridgecheck.git
```

Open the project in Android Studio (Ladybug or newer).

Create a `local.properties` file in the root directory (this is git-ignored for security) and add your keys:

```Properties
GEMINI_API_KEY=your_gemini_key
EDAMAM_ID=your_edamam_id
EDAMAM_KEY=your_edamam_key
```

Sync Gradle and run the app on your device or emulator.

## 📖 How It Works
**Scan:** The app uses CameraX to capture a high-resolution image.

**Analyze:** The Bitmap is processed and sent to Gemini with a specialized prompt to extract a clean list of food items.

**Sort:** The app queries Edamam and runs a client-side sorting algorithm to find the "Lowest Missing Ingredient" count.

**Display:** Results are rendered in a LazyColumn with asynchronous image loading for a smooth UX.

## ⚖️ Attributions
This project is made possible by the following amazing services:

* **[Edamam Recipe Search API](https://developer.edamam.com/):** Provides the extensive database of recipes, nutritional information, and food images.
* **[Google Gemini 3 Flash](https://aistudio.google.com/):** Powers the computer vision and ingredient extraction logic that makes "FridgeCheck" smart.
* **[Coil](https://coil-kt.github.io/coil/):** Handles the high-performance image loading and caching for the recipe results.
* **[Material Design 3](https://m3.material.io/):** For the design system and iconography used throughout the interface.

## 🛡️ License
Distributed under the MIT License. See LICENSE for more information.
