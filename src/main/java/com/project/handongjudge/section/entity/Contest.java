package com.project.handongjudge.section.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;
import javax.persistence.Column;
import javax.persistence.Table;
import lombok.*;    

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "contest")
public class Contest {  
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cid;    

    @ManyToOne
    @JoinColumn(name = "section_id")
    private Section section;

    @Column(name = "contest_id")
    private String contestId;

}
