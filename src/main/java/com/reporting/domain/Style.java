package com.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rpt_style", schema = "reporting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Style {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "style_id")
    private Integer styleId;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name; // header | section | normal | total | blank

    @Column(name = "font_size")
    private Integer fontSize = 11;

    @Column(name = "is_bold")
    private Boolean isBold = false;

    @Column(name = "border_top")
    private Boolean borderTop = false;

    @Column(name = "border_bottom")
    private Boolean borderBottom = false;

    @Column(name = "alignment", length = 10)
    private String alignment = "left"; // left | center | right

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "bg_color_hex", length = 7)
    private String bgColorHex;
}
