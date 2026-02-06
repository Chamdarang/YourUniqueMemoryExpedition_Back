package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.spotgroup.request.SpotGroupCreateRequest;
import study.yume.dto.spotgroup.request.SpotGroupUpdateRequest;
import study.yume.dto.spotgroup.response.SpotGroupDetailResponse;
import study.yume.dto.spotgroup.response.SpotGroupResponse;
import study.yume.model.SpotGroup;
import study.yume.model.SpotUser;
import study.yume.repository.SpotGroupRepository;
import study.yume.repository.SpotUserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SpotGroupService {

    private final SpotGroupRepository spotGroupRepository;
    private final SpotUserRepository spotUserRepository;

    @Transactional(readOnly = true)
    public List<SpotGroupResponse> getAllGroups(Long userId){
        return spotGroupRepository.findAllByUserId(userId).stream()
                .map(SpotGroupResponse::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SpotGroupDetailResponse getGroupById(Long userId, Long groupId){
        return SpotGroupDetailResponse.toDto(findGroupByUserIdAndId(userId, groupId));
    }

    public SpotGroupResponse createGroup(Long userId, SpotGroupCreateRequest req){
        SpotGroup group = new SpotGroup();
        group.setUserId(userId);
        group.setGroupName(req.groupName());

        return SpotGroupResponse.toDto(spotGroupRepository.save(group));
    }

    public SpotGroupResponse updateGroup(Long userId, Long groupId, SpotGroupUpdateRequest req){
        SpotGroup group = findGroupByUserIdAndId(userId, groupId);
        group.setGroupName(req.groupName());

        return SpotGroupResponse.toDto(spotGroupRepository.save(group));
    }

    public void deleteGroup(Long userId, Long groupId){
        SpotGroup group = findGroupByUserIdAndId(userId, groupId);

        for(SpotUser spotUser : group.getSpotUsers()){
            spotUser.getSpotGroup().remove(group);
            spotUserRepository.save(spotUser);
        }

        spotGroupRepository.delete(group);
    }

    public void addSpotToGroup(Long userId, Long groupId, Long spotUserId){
        SpotGroup group = findGroupByUserIdAndId(userId, groupId);
        SpotUser spotUser= findSpotUserUserByUserIdAndId(userId, spotUserId);

        if(!spotUser.getSpotGroup().contains(group)){
            spotUser.getSpotGroup().add(group);
            spotUserRepository.save(spotUser);
        }else{
            throw new IllegalArgumentException("이미 해당 그룹에 등록되어 있습니다");
        }
    }

    public void removeSpotFromGroup(Long userId, Long groupId, Long spotUserId){
        SpotGroup group = findGroupByUserIdAndId(userId, groupId);
        SpotUser spotUser= findSpotUserUserByUserIdAndId(userId, spotUserId);

        if(spotUser.getSpotGroup().contains(group)){
            spotUser.getSpotGroup().remove(group);
            spotUserRepository.save(spotUser);
        }else{
            throw new IllegalArgumentException("해당 그룹에 등록되어 있지 않습니다");
        }
    }


    private SpotGroup findGroupByUserIdAndId(Long userId, Long groupId) {
        return spotGroupRepository.findByUserIdAndId(userId, groupId)
                .orElseThrow(() -> new EntityNotFoundException("해당 그룹을 찾을 수 없습니다."));
    }
    private SpotUser findSpotUserUserByUserIdAndId(Long userId, Long spotUserId) {
        return spotUserRepository.findByUserIdAndId(userId, spotUserId)
                .orElseThrow(() -> new EntityNotFoundException("해당 장소를 찾을 수 없습니다."));
    }
}
