// stapel-image: the pure image model brick UI compiles against — the backend media DTO (StapelImage,
// VariantMeta), the variant-ladder tier math ported from stapel-react's packages/image, and the
// request / placeholder / error model of a load. No Compose, no Coil (stapel-image-compose loads).
plugins {
    id("stapel.substrate")
    id("stapel.serialization")
}

stapel {
    description = "Image model for Stapel clients: the media DTO, variant-ladder tier selection, load request, placeholder and error types."
}
