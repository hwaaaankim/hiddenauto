package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.task.Task;

/** 업체 변경 전의 공통 메모는 고객에게 노출되는 Task와 분리해 보관한다. */
@Service
public class TaskCompanyNoteArchive {
    public record ArchivedNote(long fromCompanyId, long toCompanyId, String actorUsername,
            LocalDateTime archivedAt, String customerNote, String internalNote) {}

    private final JdbcTemplate db;

    public TaskCompanyNoteArchive(@Qualifier("dataSource") DataSource dataSource) {
        this.db = new JdbcTemplate(dataSource);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void archiveForTransfer(Task original, Long toCompanyId, String actorUsername) {
        String customerNote = original.getCustomerNote();
        String internalNote = original.getInternalNote();
        if ((customerNote != null && !customerNote.isEmpty()) || (internalNote != null && !internalNote.isEmpty())) {
            db.update("""
                    INSERT INTO tb_task_company_note_archive
                        (task_id, from_company_id, to_company_id, actor_username, customer_note, internal_note)
                    VALUES (?,?,?,?,?,?)
                    """, original.getId(), original.getRequestedBy().getCompany().getId(), toCompanyId,
                    actorUsername, customerNote, internalNote);
        }
        original.setCustomerNote(null);
        original.setInternalNote(null);
    }

    @Transactional(readOnly = true)
    public List<ArchivedNote> forManagement(long taskId) {
        return db.query("""
                SELECT from_company_id, to_company_id, actor_username, archived_at, customer_note, internal_note
                FROM tb_task_company_note_archive WHERE task_id=? ORDER BY id DESC
                """, (rs, row) -> new ArchivedNote(rs.getLong(1), rs.getLong(2), rs.getString(3),
                rs.getTimestamp(4).toLocalDateTime(), rs.getString(5), rs.getString(6)), taskId);
    }
}
