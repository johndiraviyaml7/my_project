Place your DICOM data here, organised as:

    PSMA_one_dicom/
      <subject_id>/
        <study_instance_uid>/
          <series_instance_uid>/
            *.dcm

The parser walks this tree on first boot when you run:
    docker compose --profile init run --rm parser_init

You can drop the PSMA_0b1200b317289bbb sample folder from the original
New_Dicom_Tool_Backend.zip directly into this directory.
