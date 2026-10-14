package org.datazuul.amadeos.audio.service.cd;

import org.datazuul.amadeos.audio.domain.CdToc;

/** An inserted audio CD: its table of contents, CD-Text and MusicBrainz disc ID. */
public record CdDisc(CdToc toc, CdText text, String discId) {
}
