SELECT board.source,
       board.source_key,
       board.canonical_url,
       board.status,
       board.failure_reason,
       board.first_discovered_at,
       board.last_discovered_at,
       board.validated_at
FROM workspace_source_board workspace_board
JOIN discovered_source_board board ON board.id = workspace_board.discovered_source_board_id
WHERE workspace_board.workspace_id = :workspaceId
ORDER BY board.source, board.source_key
