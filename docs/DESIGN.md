---
version: "1.0"
name: "Reporting Engine Platform"
description: "Design specifications and UX tokens for the premium dark-mode, glassmorphic layout builder interface"
colors:
    primary: "#6366f1"
    secondary: "#a855f7"
    tertiary: "#f8fafc"
    surface: "#1e293b"
    background: "#0f172a"
    error: "#ef4444"
    on-primary: "#ffffff"
    on-surface: "#cbd5e1"
typography:
    display-lg:
        fontFamily: "Outfit, Inter, sans-serif"
        fontSize: "28px"
        fontWeight: 700
        lineHeight: "36px"
        letterSpacing: "-0.5px"
    body-md:
        fontFamily: "Outfit, Inter, sans-serif"
        fontSize: "15px"
        fontWeight: 400
        lineHeight: "22px"
        letterSpacing: "0px"
rounded:
    sm: "6px"
    md: "12px"
    lg: "24px"
    full: "50%"
spacing:
    sm: "8px"
    md: "16px"
    lg: "32px"
components:
    card:
        background: "rgba(30, 41, 59, 0.7)"
        backdropFilter: "blur(16px)"
        border: "1px solid rgba(255, 255, 255, 0.1)"
    button:
        background: "linear-gradient(135deg, #6366f1 0%, #a855f7 100%)"
        borderRadius: "12px"
---

# Visual Design and UX Guidelines

This document serves as the single source of truth for the visual identity and user experience.

## Overview

The Reporting Engine platform is designed to look modern, professional, and visually striking. It targets business analysts and developers, using a premium dark-mode interface with subtle glow animations and glassmorphic card overlays. This helps reduce cognitive load when editing complex tabular grid mappings.

## Colors

The application relies on a dark-slate base with indigo and purple accents:

- **Base Background**: Radial gradient starting at `#0f172a` (Slate 900) to `#1e293b` (Slate 800) at 90% radius.
- **Surface Cards**: Semi-transparent `#1e293b` with 70% opacity and `backdrop-filter: blur(16px)` to achieve a glassmorphism feel.
- **Accent Elements**: Linear gradients running from `#6366f1` (Indigo 500) to `#a855f7` (Purple 500).
- **Text Elements**: High-contrast slate variants (`#f8fafc` for titles, `#cbd5e1` for body text, and `#94a3b8` for descriptors/placeholders).

## Typography

The typography hierarchy uses the **Outfit** font family (Google Fonts) for display and header elements to give a modern, tech-forward voice. Fallbacks are configured to **Inter** and browser default sans-serif.

| Token        | size | weight | usage                                              |
| :----------- | :--- | :----- | :------------------------------------------------- |
| `display-lg` | 28px | 700    | Primary Titles (e.g. login, builder pages)         |
| `header-md`  | 20px | 600    | Card sections and component headers                |
| `body-md`    | 15px | 400    | Grid values, descriptions, inputs                  |
| `label-sm`   | 13px | 600    | Input labels (uppercase with 0.5px letter spacing) |

## Layout

The UI uses standard CSS grid and flexbox flows:

- **Spacing Guidelines**: Core layout elements use spacing tokens in multiples of `8px` (e.g., `8px`, `16px`, `24px`, `32px` padding).
- **Grid Layout**: The report builder uses a flex layout table grid mapping cells directly in a row-by-column intersection sheet.

## Elevation & Depth

Visual depth is achieved through layering and shadows:

- **Layer 0**: Main page background (dark gradient).
- **Layer 1 (Cards)**: Glass cards with fine `1px` borders (`rgba(255, 255, 255, 0.1)`) and soft blur shadows (`box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3)`).
- **Layer 2 (Popups/Active Input)**: Focused inputs and interactive elements project a glow overlay (`box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2)`).

## Shapes

Edge treatments are soft and calculated:

- **Cards/Containers**: Rounded at `24px` (`border-radius: 24px`).
- **Inputs/Buttons**: Rounded at `12px` (`border-radius: 12px`).
- **Icons/Badges**: Circle treatment (`50%` or pill-shaped `9999px` corners).

## Components

Key elements are designed with consistent transitions:

- **Submit Buttons**: Gradient background with transition duration of `0.2s` for hover effects. Disabled state decreases opacity to `0.5` and sets cursor to `not-allowed`.
- **Drag-and-Drop Zones**: Dashed border styles (`rgba(99, 102, 241, 0.4)`) highlighting when active items are dragged over them.

## Do's and Don't's

- **Do** use uppercase titles for input labels with small letter-spacing.
- **Do** apply `backdrop-filter: blur(16px)` on any card layer overlay.
- **Don't** use standard sharp border containers or solid opaque cards.
- **Don't** mix non-rounded buttons with rounded inputs.
