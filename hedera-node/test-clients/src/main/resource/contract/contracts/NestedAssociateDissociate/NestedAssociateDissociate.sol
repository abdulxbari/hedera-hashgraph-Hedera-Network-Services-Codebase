// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;

import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract NestedAssociateDissociateContract {
    IHederaTokenService constant HederaTokenService = IHederaTokenService(address(0x167));

    AssociateDissociateContract associateDissociateContract;

    constructor(address associateDissociateContractAddress) public {
        associateDissociateContract = AssociateDissociateContract(associateDissociateContractAddress);
    }

    function associateDissociateContractCall(address sender, address tokenAddress) external {
        associateDissociateContract.tokenAssociate(sender, tokenAddress);
        int response = HederaTokenService.dissociateToken(sender, tokenAddress);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Dissociate Failed");
        }
    }

    function dissociateAssociateContractCall(address sender, address tokenAddress) external {
        int response = HederaTokenService.dissociateToken(sender, tokenAddress);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Dissociate Failed");
        }
        associateDissociateContract.tokenAssociate(sender, tokenAddress);
    }

    function associateStaticCall(address sender, address tokenAddress) external view {
        (bool success, bytes memory result) = address(associateDissociateContract).staticcall(abi.encodeWithSignature("tokenAssociate(address,address)", sender, tokenAddress));
        if (!success) {
            revert("Static associate call failed!");
        }
    }

    function dissociateStaticCall(address sender, address tokenAddress) external view{
        (bool success, bytes memory result) = address(associateDissociateContract).staticcall(abi.encodeWithSignature("tokenDissociate(address,address)", sender, tokenAddress));
        if (!success) {
            revert("Static dissociate call failed!");
        }
    }

    function associateDelegateCall(address sender, address tokenAddress) external {
        (bool success, bytes memory result) = address(associateDissociateContract).delegatecall(abi.encodeWithSignature("tokenAssociate(address,address)", sender, tokenAddress));
        if (!success) {
            revert("Delegate associate call failed!");
        }
    }

    function dissociateDelegateCall(address sender, address tokenAddress) external {
        (bool success, bytes memory result) = address(associateDissociateContract).delegatecall(abi.encodeWithSignature("tokenDissociate(address,address)", sender, tokenAddress));
        if (!success) {
            revert("Delegate dissociate call failed!");
        }
    }
}

contract AssociateDissociateContract {
    IHederaTokenService constant HederaTokenService = IHederaTokenService(address(0x167));

    function tokenAssociate(address sender, address tokenAddress) external {
        HederaTokenService.associateToken(sender, tokenAddress);
    }

    function tokenDissociate(address sender, address tokenAddress) external {
        HederaTokenService.dissociateToken(sender, tokenAddress);
    }
}