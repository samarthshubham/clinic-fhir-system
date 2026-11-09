package io.github.samarthshubham.payer.controller;

import io.github.samarthshubham.payer.dto.MemberResponse;
import io.github.samarthshubham.payer.dto.MemberSummary;
import io.github.samarthshubham.payer.dto.PageResponse;
import io.github.samarthshubham.payer.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {

  private final MemberService memberService;

  public MemberController(MemberService memberService) {
    this.memberService = memberService;
  }

  @PreAuthorize("hasAuthority('member:read')")
  @GetMapping
  public ResponseEntity<PageResponse<MemberSummary>> searchMembers(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String memberId) {
    return ResponseEntity.ok(memberService.searchMembers(name, memberId));
  }

  @PreAuthorize("hasAuthority('member:read')")
  @GetMapping("/{id}")
  public ResponseEntity<MemberResponse> getMember(@PathVariable String id) {
    return ResponseEntity.ok(memberService.getMember(id));
  }
}
