package org.example.application.services.event;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.event.*;
import org.example.application.exception.event.EventException;
import org.example.domain.entity.User;
import org.example.domain.entity.event.Event;
import org.example.domain.entity.event.EventPost;
import org.example.domain.entity.event.EventPostComment;
import org.example.domain.entity.event.EventPostLike;
import org.example.domain.entity.event.EventPostPollVote;
import org.example.domain.repository.UserRepository;
import org.example.domain.repository.event.EventPostCommentRepository;
import org.example.domain.repository.event.EventPostRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor
public class EventPostService {

    private static final int MIN_POLL_OPTIONS = 2;
    private static final int MAX_POLL_OPTIONS = 6;
    private static final int MAX_POLL_OPTION_LENGTH = 120;
    private static final int MAX_LOCATION_LENGTH = 300;

    private final EventPostRepository postRepository;
    private final EventPostCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final EventAuthorizationService authorizationService;
    private final EventMapper eventMapper;
    private final EventValidationUtils validationUtils;

    @Transactional
    public EventPostsPageDTO listPosts(UUID eventId, UUID userId, int limit, String nextToken) {
        authorizationService.assertCanView(eventId, userId);
        int pageSize = limit > 0 ? Math.min(limit, 50) : 20;
        EventMapper.Cursor cursor = eventMapper.decodeCursor(nextToken);

        List<EventPost> posts =
                postRepository.findByEventId(
                        eventId,
                        pageSize + 1,
                        cursor != null ? cursor.timestamp() : null,
                        cursor != null ? cursor.id() : null);

        String newToken = null;
        if (posts.size() > pageSize) {
            EventPost last = posts.get(pageSize - 1);
            newToken = eventMapper.encodeCursor(last.getPostedAt(), last.getId());
            posts = posts.subList(0, pageSize);
        }

        List<EventPostResponseDTO> items =
                posts.stream().map(p -> toPostResponse(p, userId)).collect(Collectors.toList());

        return EventPostsPageDTO.builder().items(items).nextToken(newToken).build();
    }

    @Transactional
    public EventPostResponseDTO createPost(UUID eventId, CreateEventPostRequestDTO request, UUID userId) {
        Event event = authorizationService.assertCanPost(eventId, userId);
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            throw EventException.validation("text is required");
        }

        String text = validationUtils.sanitizeText(request.getText(), 4000);
        if (request.getImageUrl() != null) {
            validationUtils.validateImageUrl(request.getImageUrl());
        }

        String location = null;
        if (request.getLocation() != null && !request.getLocation().isBlank()) {
            location = validationUtils.sanitizeText(request.getLocation(), MAX_LOCATION_LENGTH);
        }

        List<String> pollOptions = normalizePollOptions(request.getPollOptions());

        User author = userRepository.findById(userId);
        EventPost post =
                EventPost.builder()
                        .event(event)
                        .author(author)
                        .text(text)
                        .imageUrl(request.getImageUrl())
                        .location(location)
                        .pollOptions(pollOptions)
                        .build();
        postRepository.persist(post);
        return toPostResponse(post, userId);
    }

    @Transactional
    public EventPostResponseDTO votePoll(
            UUID eventId, UUID postId, VoteEventPostPollRequestDTO request, UUID userId) {
        authorizationService.assertCanView(eventId, userId);
        EventPost post =
                postRepository
                        .findActiveById(postId)
                        .filter(p -> p.getEvent().getId().equals(eventId))
                        .orElseThrow(EventException::notFound);

        List<String> options = post.getPollOptions();
        if (options == null || options.isEmpty()) {
            throw EventException.validation("Post does not have a poll");
        }
        if (request == null || request.getOptionIndex() == null) {
            throw EventException.validation("optionIndex is required");
        }
        int optionIndex = request.getOptionIndex();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            throw EventException.validation("Invalid poll option");
        }

        EventPostPollVote vote =
                postRepository
                        .findPollVote(postId, userId)
                        .orElseGet(
                                () ->
                                        EventPostPollVote.builder()
                                                .postId(postId)
                                                .userId(userId)
                                                .build());
        vote.setOptionIndex(optionIndex);
        postRepository.getEntityManager().persist(vote);

        return toPostResponse(post, userId);
    }

    @Transactional
    public void deletePost(UUID eventId, UUID postId, UUID userId) {
        authorizationService.assertCanView(eventId, userId);
        EventPost post =
                postRepository
                        .findActiveById(postId)
                        .filter(p -> p.getEvent().getId().equals(eventId))
                        .orElseThrow(EventException::notFound);

        boolean isOrganizer = authorizationService.canEdit(post.getEvent(), userId);
        boolean isAuthor = post.getAuthor().id.equals(userId);
        if (!isOrganizer && !isAuthor) {
            throw EventException.notFound();
        }

        post.setDeletedAt(Instant.now());
        postRepository.persist(post);
    }

    @Transactional
    public EventPostResponseDTO updatePost(
            UUID eventId, UUID postId, UpdateEventPostRequestDTO request, UUID userId) {
        authorizationService.assertCanEdit(eventId, userId);
        EventPost post =
                postRepository
                        .findActiveById(postId)
                        .filter(p -> p.getEvent().getId().equals(eventId))
                        .orElseThrow(EventException::notFound);

        if (request == null || request.getPinned() == null) {
            throw EventException.validation("pinned is required");
        }

        boolean pinned = Boolean.TRUE.equals(request.getPinned());
        post.setPinned(pinned);
        post.setPinnedAt(pinned ? Instant.now() : null);
        post.setUpdatedAt(Instant.now());
        postRepository.persist(post);
        return toPostResponse(post, userId);
    }

    @Transactional
    public EventPostResponseDTO likePost(UUID eventId, UUID postId, UUID userId) {
        authorizationService.assertCanView(eventId, userId);
        EventPost post =
                postRepository
                        .findActiveById(postId)
                        .filter(p -> p.getEvent().getId().equals(eventId))
                        .orElseThrow(EventException::notFound);

        if (!postRepository.isLikedByUser(postId, userId)) {
            EventPostLike like =
                    EventPostLike.builder().postId(postId).userId(userId).build();
            postRepository.getEntityManager().persist(like);
        }

        return toPostResponse(post, userId);
    }

    @Transactional
    public EventPostCommentsPageDTO listComments(UUID eventId, UUID postId, UUID userId) {
        authorizationService.assertCanView(eventId, userId);
        postRepository
                .findActiveById(postId)
                .filter(p -> p.getEvent().getId().equals(eventId))
                .orElseThrow(EventException::notFound);

        List<EventPostCommentResponseDTO> items =
                commentRepository.findByPostId(postId).stream()
                        .map(this::toCommentResponse)
                        .collect(Collectors.toList());

        return EventPostCommentsPageDTO.builder().items(items).build();
    }

    @Transactional
    public EventPostCommentResponseDTO createComment(
            UUID eventId, UUID postId, CreateEventPostCommentRequestDTO request, UUID userId) {
        authorizationService.assertCanView(eventId, userId);
        EventPost post =
                postRepository
                        .findActiveById(postId)
                        .filter(p -> p.getEvent().getId().equals(eventId))
                        .orElseThrow(EventException::notFound);

        if (request == null || request.getText() == null || request.getText().isBlank()) {
            throw EventException.validation("text is required");
        }

        String text = validationUtils.sanitizeText(request.getText(), 1000);
        User author = userRepository.findById(userId);

        EventPostComment comment =
                EventPostComment.builder().post(post).author(author).text(text).build();
        commentRepository.persist(comment);
        return toCommentResponse(comment);
    }

    @Transactional
    public void deleteComment(UUID eventId, UUID postId, UUID commentId, UUID userId) {
        authorizationService.assertCanView(eventId, userId);
        EventPost post =
                postRepository
                        .findActiveById(postId)
                        .filter(p -> p.getEvent().getId().equals(eventId))
                        .orElseThrow(EventException::notFound);

        EventPostComment comment =
                commentRepository
                        .findActiveById(commentId)
                        .filter(c -> c.getPost().getId().equals(post.getId()))
                        .orElseThrow(EventException::notFound);

        boolean isOrganizer = authorizationService.canEdit(post.getEvent(), userId);
        boolean isAuthor = comment.getAuthor().id.equals(userId);
        if (!isOrganizer && !isAuthor) {
            throw EventException.notFound();
        }

        comment.setDeletedAt(Instant.now());
        commentRepository.persist(comment);
    }

    private List<String> normalizePollOptions(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String option : raw) {
            if (option == null || option.isBlank()) {
                continue;
            }
            String cleaned = validationUtils.sanitizeText(option, MAX_POLL_OPTION_LENGTH);
            if (cleaned != null && !cleaned.isBlank()) {
                unique.add(cleaned);
            }
        }
        if (unique.isEmpty()) {
            return null;
        }
        if (unique.size() < MIN_POLL_OPTIONS) {
            throw EventException.validation("Poll requires at least " + MIN_POLL_OPTIONS + " options");
        }
        if (unique.size() > MAX_POLL_OPTIONS) {
            throw EventException.validation("Poll allows at most " + MAX_POLL_OPTIONS + " options");
        }
        return new ArrayList<>(unique);
    }

    private EventPostResponseDTO toPostResponse(EventPost post, UUID userId) {
        UUID postId = post.getId();
        return EventPostResponseDTO.builder()
                .postId(postId)
                .eventId(post.getEvent().getId())
                .authorId(post.getAuthor().id)
                .authorName(post.getAuthor().getFullName())
                .text(post.getText())
                .imageUrl(post.getImageUrl())
                .location(post.getLocation())
                .poll(toPollDto(post, userId))
                .postedAt(post.getPostedAt())
                .likeCount(postRepository.countLikes(postId))
                .commentCount(postRepository.countComments(postId))
                .likedByMe(postRepository.isLikedByUser(postId, userId))
                .pinned(post.isPinned())
                .pinnedAt(post.getPinnedAt())
                .build();
    }

    private EventPostPollDTO toPollDto(EventPost post, UUID userId) {
        List<String> options = post.getPollOptions();
        if (options == null || options.isEmpty()) {
            return null;
        }
        Map<Integer, Long> counts = postRepository.countPollVotesByOption(post.getId());
        List<EventPostPollOptionDTO> optionDtos = new ArrayList<>(options.size());
        long total = 0;
        for (int i = 0; i < options.size(); i++) {
            long voteCount = counts.getOrDefault(i, 0L);
            total += voteCount;
            optionDtos.add(
                    EventPostPollOptionDTO.builder()
                            .index(i)
                            .text(options.get(i))
                            .voteCount(voteCount)
                            .build());
        }
        Integer myVote =
                postRepository
                        .findPollVote(post.getId(), userId)
                        .map(EventPostPollVote::getOptionIndex)
                        .orElse(null);
        return EventPostPollDTO.builder()
                .options(optionDtos)
                .totalVotes(total)
                .myVoteIndex(myVote)
                .build();
    }

    private EventPostCommentResponseDTO toCommentResponse(EventPostComment comment) {
        return EventPostCommentResponseDTO.builder()
                .commentId(comment.getId())
                .postId(comment.getPost().getId())
                .authorId(comment.getAuthor().id)
                .authorName(comment.getAuthor().getFullName())
                .text(comment.getText())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
