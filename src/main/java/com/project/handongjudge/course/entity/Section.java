//package com.project.handongjudge.course.entity;
//
//import com.project.handongjudge.user.entity.User;
//import lombok.*;
//
//import javax.persistence.*;
//
//@Entity
//@Getter @Setter
//@NoArgsConstructor
//@AllArgsConstructor
//@Builder
//public class Section {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "course_id")
//    private Course course;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "instructor_id")
//    private User instructor;
//
//    private int sectionNumber;
//}
