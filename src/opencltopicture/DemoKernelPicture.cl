

const sampler_t sampler_linear = CLK_NORMALIZED_COORDS_TRUE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;


__kernel void doVigneting(read_only image2d_t input, write_only image2d_t output) {		
    
    // Recuperation des indices
    const int2 xy = (int2)(get_global_id(0),
                           get_global_id(1)),
               res = get_image_dim(output);

    // Test defensif pour pas ecrire a coté
    if (xy.x>=res.x || xy.y>=res.y) return;
    
    // Recuperation de la coordonee entre 0 et 1
    const float2 uv = convert_float2(xy)/convert_float2(res);

    // lecture de la couleur dans la texture d'entree 
 //   int4 iCol = read_imagei(input, sampler_linear, uv);  // 0 .. 255   bgra
    
    // on la passe en valeur entre 0 et 1 en rgb => c'est plus pratique;
    float3 fCol = read_imagef(input, sampler_linear, uv).zyx; // bgra to rgba
    
    // Vigneting romantique
    float3 outCol = mix((float3)(0.f,0.f,1.f), fCol, smoothstep(.01f,.02f,fabs(length(uv-(float2)(.5f))-.3f)));
    
    // On passe le resultat en sortie rgb => bgr
    write_imagef(output,xy, (float4)(outCol.zyx,1.f));
}


    
    
// Need some text at end to avoid nvidia compilation error ! 
    
    
    
    